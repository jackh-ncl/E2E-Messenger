package com.e2eMessenger;

import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.NavUtils;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.text.Editable;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.CursorAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.e2eMessenger.Message.MessageType;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Class that handles functionality for a conversation activity
 * with a specific contact.

 *
 * @author Jack Hindmarch
 */
public class ConversationActivity extends ActionBarActivity
{
	// Used to determine whether or not user should receive message notifications
	public static Contact currentConversationPartner = null;

	private AtomicInteger msgId = new AtomicInteger();
	private String from;
	private Contact contact;
	private long contactID;
	private MessageAdapter adapter;
	private BroadcastReceiver broadcastReceiver;

	private boolean firstLoad = true;
	private boolean cameFromNotification = false;

	// Views
	private RelativeLayout messageInputLayout;
	private EditText input;
	private ImageView send;
	private ListView messageList;

	private Intent starterIntent;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_conversation);
		starterIntent = getIntent();

		// Show the Up button in the action bar.
		ActionBar actionBar = getSupportActionBar();
		actionBar.setDisplayHomeAsUpEnabled(true);

		// Fetch the user's username needed for attaching to sent messages
		final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		from = prefs.getString(SettingsActivity.USERNAME, "error");
		
		// Get contact id form intent
		this.contactID = getIntent().getLongExtra("uid", -1);
		System.out.println("Contact ID = " + contactID);

		// Fetch contact from id
		DatabaseHelper db = DatabaseHelper.getInstance(this);
		this.contact = db.getContact(contactID);

		// Needed in case notification is clicked after removing contact
		if(contact == null)
		{
			CharSequence text = "Contact no longer exists.";
			int duration = Toast.LENGTH_LONG;
			Toast toast = Toast.makeText(this, text, duration);
			toast.show();
			finish();
			return;
		}

		//setTitle(contact.getNick());
		actionBar.setTitle(contact.getNick());

		messageInputLayout = (RelativeLayout) findViewById(R.id.message_new);
		input = (EditText) findViewById(R.id.message_input);

		/* Load messages and place them into list view adapter */
		messageList = (ListView) findViewById(R.id.message_list);
		messageList.setStackFromBottom(true);
		messageList.setTranscriptMode(ListView.TRANSCRIPT_MODE_ALWAYS_SCROLL); // always scroll to bottom
		// Set adapter for message list
		Cursor messages = db.getAllMessages(contactID);
		messages.moveToFirst();

		adapter = new MessageAdapter(this, messages);
		messageList.setAdapter(adapter);
		db.close();

		/**
		 * When a message is clicked, copy its context to the device's clipboard.
		 */
		messageList.setOnItemClickListener(new AdapterView.OnItemClickListener()
		{
			@Override
			public void onItemClick(AdapterView<?> adapterView, View view, int i, long id)
			{
				DatabaseHelper db = DatabaseHelper.getInstance(ConversationActivity.this); // Possible to take directly from view rather than load from db?
				Message msg = db.getMessage(id);

				// Copy to clipboard
				ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
				clipboard.setPrimaryClip(ClipData.newPlainText("message", msg.getMessage()));

				// Make a new toast
				CharSequence text = "Copied to clipboard.";
				int duration = Toast.LENGTH_SHORT;
				Toast toast = Toast.makeText(ConversationActivity.this, text, duration);
				toast.show();
			}
		});

		messageList.setOnScrollListener(new AbsListView.OnScrollListener()
		{
			@Override
			public void onScrollStateChanged(AbsListView view, int scrollState)
			{

			}

			@Override
			public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount)
			{
				setShouldReceiveNotifications();
			}
		});

		send = (ImageView) findViewById(R.id.message_send);
		checkValidContact();

		/**
		 * When send button is clicked, save and encrypt the composed message,
		 * then pack it into a JSON encoded string which is ready to be
		 * sent to the server.
		 */
		send.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v)
			{
				System.out.println("count = " + messageList.getCount());
				System.out.println("firstView = " + messageList.getFirstVisiblePosition());
				System.out.println("lastView = " + messageList.getLastVisiblePosition());
				System.out.println("bottom = " + messageList.getBottom());
				Editable msgField = input.getText();
				String msg = msgField.toString();

				if(!msg.isEmpty())
				{
					msgField.clear();

					// save message to db
					DatabaseHelper db = DatabaseHelper.getInstance(ConversationActivity.this);
					Message message = new Message();
					message.setContactID(contactID);
					message.setSent(true);
					message.setMessage(msg);
					message.setTimestamp(System.currentTimeMillis());
					message.setSuccess(true);
					long messageDBID = db.createMessage(message);
					db.close();

					refreshMessageList();

					// attempt to encrypt message using shared key
					String keyString = contact.getSharedKey();
					AES aes = new AES(keyString);
					byte[] encBytes = null;

					try {
						encBytes = aes.encrpyt(msg.getBytes());
					} catch(Exception e)
					{
						e.printStackTrace();
						JPAKE jpake = new JPAKE(ConversationActivity.this);
						jpake.handleError(contact.getUsername(), ConversationActivity.this);
						jpake.sendErrorMessage(contact.getUsername());
						return;
					}

					String IV = Base64.encodeToString(aes.getIV(), Base64.DEFAULT).replaceAll("\n", "");
					String encMsg = Base64.encodeToString(encBytes, Base64.DEFAULT).replaceAll("\n", "");

					// Send message to server
					String to = contact.getUsername();

					// get session key
					SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ConversationActivity.this);
					String session_key = prefs.getString(SettingsActivity.SESSION_KEY, "");

					try	{
						JSONObject data = new JSONObject();
						data.put("to", to);
						data.put("from", from);
						data.put("session_key", session_key);
						data.put("message_type", MessageType.MESSAGE.ordinal());
						String id = Integer.toString(msgId.incrementAndGet());
						data.put("message_id", id);
						data.put("message", encMsg);
						data.put("IV", IV);

						Bundle extras = new Bundle();
						extras.putLong("messageDBID", messageDBID);

						Message.send(data, MessageType.MESSAGE, extras, ConversationActivity.this);

					} catch(JSONException e) {
						e.printStackTrace();
					}
				}
			}
		});

		/**
		 * Refresh the message list when a broadcast is received.
		 *
		 * <p>Allows for UI to update whenever a new message is received.</p>
		 */
		broadcastReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				refreshMessageList();
			}
		};
	}

	private void checkValidContact()
	{
		// Only allow user to send messages if there is a valid shared key with the contact.
		if(contact.getStatus() != Contact.Status.VALID || contactIsBlocked(contact.getUsername()))
		{
			//input.setEnabled(false);
			//send.setEnabled(false);
			messageInputLayout.setVisibility(RelativeLayout.GONE);

			// Make a new toast
			CharSequence m1 = "No valid key for " + contact.getNick() + ".";
			CharSequence text = (contactIsBlocked(contact.getUsername())) ? "Contact is blocked." : m1;
			int duration = Toast.LENGTH_LONG;
			Toast toast = Toast.makeText(ConversationActivity.this, text, duration);
			toast.show();
		}
	}

	private boolean contactIsBlocked(String username)
	{
		DatabaseHelper db = DatabaseHelper.getInstance(this);
		HashSet<String> blockedList = db.getBlockedUserHashSet();
		return blockedList.contains(username);
	}

	/**
	 * Reload the conversation from the database and put it in a new adapter.
	 *
	 * <p>Must be done this way to work, as simply setting the adapter's data does nothing.</p>
	 */
	private void refreshMessageList()
	{
		// Save index and top position
		int count = messageList.getCount();
		int firstIndex = messageList.getFirstVisiblePosition();
		int lastIndex = messageList.getLastVisiblePosition();
		View v = messageList.getChildAt(0);
		int top = (v == null) ? 0 : v.getTop();

		DatabaseHelper db = DatabaseHelper.getInstance(ConversationActivity.this);
		MessageAdapter adapter = new MessageAdapter(this, db.getAllMessages(contactID));
		this.adapter = adapter;
		messageList.setAdapter(adapter);

		// Check contact key is still valid
		this.contact = db.getContact(this.contactID);
		checkValidContact();
		db.close();

		// Restore index and scroll pos if last message is not in sight
		if(firstLoad || cameFromNotification || count == lastIndex + 1)
		{
			messageList.setSelection(messageList.getCount());
		}
		else // ListView should scroll to bottom on first load
		{
			messageList.setSelectionFromTop(firstIndex, top);
		}
	}

	@Override
	protected void onNewIntent(Intent intent)
	{
		super.onNewIntent(intent);
		cameFromNotification = true;

		Bundle extras = intent.getExtras();
		long uid = extras.getLong("uid");

		if(contactID != uid)
		{
			DatabaseHelper db = DatabaseHelper.getInstance(this);
			contact = db.getContact(uid);
			contactID = uid;

			// Update user interface to new contact's details
			getSupportActionBar().setTitle(contact.getNick());
			refreshMessageList();
		}
	}

	@Override
	protected void onPause()
	{
		super.onPause();

		currentConversationPartner = null;

		// Unregister since the activity is not visible
		LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver);

		// Set last read time to now to determine whether or not any unread messages exist
		DatabaseHelper db = DatabaseHelper.getInstance(this);
		Contact contact = db.getContact(contactID);
		contact.setLastReadTime(System.currentTimeMillis());
		db.updateContact(contact);
		db.close();
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		refreshMessageList();
		firstLoad = false;
		cameFromNotification = false;
		setShouldReceiveNotifications();

		// Register receiver to receive messages.
		LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, new IntentFilter("refresh"));
	}

	private void setShouldReceiveNotifications()
	{
		int lastPos = messageList.getLastVisiblePosition();
		int count = messageList.getCount();

		if(count != lastPos + 1)
		{
			currentConversationPartner = null;
		}
		else
		{
			currentConversationPartner = contact;
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		// Inflate the menu; this adds items to the action bar if it is present.
		super.onCreateOptionsMenu(menu);
		getMenuInflater().inflate(R.menu.message_screen, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
			case android.R.id.home:
				NavUtils.navigateUpFromSameTask(this);
				return true;
			case R.id.action_settings:
				Intent intent = new Intent(this, SettingsActivity.class);
				startActivity(intent);
		}
		return super.onOptionsItemSelected(item);
	}

	/**
	 * Adapter for the conversation's messages.
	 *
	 * <p>Inflates a different layout depending on whether a message was sent or received.</p>
	 */
	public class MessageAdapter extends CursorAdapter
	{
		private final LayoutInflater mInflater;
		private Cursor cursor;

		public MessageAdapter(Context context, Cursor cursor)
		{
			super(context, cursor, false);
			mInflater = LayoutInflater.from(context);
			this.cursor = cursor;
		}

		@Override
		public View newView(Context context, Cursor cursor, ViewGroup parent)
		{
			View view = null;

			if(getItemViewType(cursor.getPosition()) == 0)
			{
				view = mInflater.inflate(R.layout.message_row_sent_layout, parent, false);
				view.setTag(R.id.sent_message, view.findViewById(R.id.sent_message));
				view.setTag(R.id.sent_timedate, view.findViewById(R.id.sent_timedate));
			}
			else
			{
				view = mInflater.inflate(R.layout.message_row_received_layout, parent, false);
				view.setTag(R.id.received_message, view.findViewById(R.id.received_message));
				view.setTag(R.id.received_timedate, view.findViewById(R.id.received_timedate));
			}
			
			return view;
		}

		@Override
		public void bindView(View view, Context context, Cursor cursor)
		{
			String msg = cursor.getString(cursor.getColumnIndex(DatabaseHelper.KEY_MESSAGE));
			long timestamp = cursor.getLong(cursor.getColumnIndex(DatabaseHelper.KEY_TIMESTAMP));
			String date = new SimpleDateFormat("dd/MM/yyyy, HH:mm").format(new Date(timestamp));

			TextView msgText = null;
			TextView timestampText = null;
			
			if(getItemViewType(cursor.getPosition()) == 0)
			{
				msgText = (TextView) view.getTag(R.id.sent_message);
				timestampText = (TextView) view.getTag(R.id.sent_timedate);
				timestampText.setText(date);
			}
			else 
			{
				msgText = (TextView) view.getTag(R.id.received_message);
				timestampText = (TextView) view.getTag(R.id.received_timedate);
				timestampText.setText(date);
			}

			if(!sendSuccessful(cursor.getPosition()))
			{
				timestampText.setText("not sent...");
			}
			
			msgText.setText(msg);
		}
		
		@Override
		public int getViewTypeCount()
		{
			return 2;
		}
		
		@Override
		public int getItemViewType(int pos)
		{
			cursor.moveToPosition(pos);
			return (cursor.getInt(cursor.getColumnIndex(DatabaseHelper.KEY_SENT)) == 1) ? 0 : 1;
		}

		public boolean sendSuccessful(int pos)
		{
			cursor.moveToPosition(pos);
			return cursor.getInt(cursor.getColumnIndex(DatabaseHelper.KEY_SUCCESS)) == 1;
		}
	}
}
