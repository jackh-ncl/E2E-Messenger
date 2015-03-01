package com.e2eMessenger;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CursorAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

/**
 * Class that handles functionality of the conversations list screen.
 *
 * <p>Simply fetches all the current conversations with each contact, sorted by
 * last activity time.</p>
 *
 * <p>Clicking a conversation opens up its conversation activity.</p>
 *
 * @author Jack Hindmarch
 */
public class ConversationsFragment extends Fragment
{
	private ConversationsAdapter adapter;
	private ListView conversationList;
	private BroadcastReceiver broadcastReceiver;
	private TextView noConversations;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setHasOptionsMenu(true);

		broadcastReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				refreshAdapter();
			}
		};
	}

	@Override
	public void onResume()
	{
		super.onResume();
		refreshAdapter();
		LocalBroadcastManager.getInstance(getActivity()).registerReceiver(broadcastReceiver, new IntentFilter("refresh"));
	}

	@Override
	public void onPause()
	{
		super.onPause();
		LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(broadcastReceiver);
	}

	/**
	 * Reload the conversation from the database and put it in a new adapter.
	 *
	 * <p>Must be done this way to work, as simply setting the adapter's data does nothing.</p>
	 */
	public void refreshAdapter()
	{
		DatabaseHelper db = DatabaseHelper.getInstance(getActivity());
		Cursor conversations = db.getMostRecentMessages();
		checkIfNoConversations(conversations);
		ConversationsAdapter adapter = new ConversationsAdapter(getActivity(), conversations);
		this.adapter = adapter;
		conversationList.setAdapter(adapter);
		db.close();
	}

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, Bundle savedInstanceState)
	{
		final View rootView = inflater.inflate(R.layout.fragment_conversations, container, false);

		// Text view saying "no conversations" to be displayed if there are none stored
		noConversations = (TextView) rootView.findViewById(R.id.no_conversations);

		// Initialise and fill up contact list
		conversationList = (ListView) rootView.findViewById(R.id.conversations_list);
		DatabaseHelper db = DatabaseHelper.getInstance(getActivity());
		Cursor conversations = db.getMostRecentMessages();
		//conversations.moveToFirst();
		checkIfNoConversations(conversations);

		adapter = new ConversationsAdapter(getActivity(), conversations);
		conversationList.setAdapter(adapter);

		// Listen for click events on contact cards
		conversationList.setOnItemClickListener(new AdapterView.OnItemClickListener()
		{
			@Override
			public void onItemClick(AdapterView<?> adapterView, View view, int i, long id)
			{
				DatabaseHelper db = DatabaseHelper.getInstance(getActivity());
				long uid = db.getUIDFromMID(id);
				Intent intent = new Intent(getActivity(), ConversationActivity.class);
				intent.putExtra("uid", uid);
				startActivity(intent);
			}
		});

		return rootView;
	}

	/**
	 * Check to see if conversation list is empty. If so, display "no conversations".
	 *
	 * @param cursor - cursor to check.
	 */
	public void checkIfNoConversations(Cursor cursor)
	{
		if(!cursor.moveToFirst())
		{
			noConversations.setVisibility(TextView.VISIBLE);
		}
		else
		{
			noConversations.setVisibility(TextView.GONE);
		}
	}
/*
	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater)
	{
		// Inflate the menu; this adds items to the action bar if it is present.
		super.onCreateOptionsMenu(menu, inflater);
		inflater.inflate(R.menu.conversations, menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch(item.getItemId())
		{
			case R.id.action_new_conversation:
				startConversation();
				return true;
			case R.id.action_settings:
				Intent intent = new Intent(getActivity(), SettingsActivity.class);
				startActivity(intent);
			default:
				return super.onOptionsItemSelected(item);

		}
	}*/

	/**
	 * Adapter that loads the card layout for each conversation in the cursor.
	 */
	class ConversationsAdapter extends CursorAdapter
	{
		private final LayoutInflater mInflater;

		public ConversationsAdapter(Context context, Cursor cursor)
		{
			super(context, cursor, false);
			mInflater = LayoutInflater.from(context);
		}

		@Override
		public View newView(Context context, Cursor cursor, ViewGroup viewGroup)
		{
			return mInflater.inflate(R.layout.conversations_card, viewGroup, false);
		}

		/**
		 * Binds the cursor data to the views in the layout.
		 */
		@Override
		public void bindView(View view, Context context, Cursor cursor)
		{
			// initialise views
			TextView nameView = (TextView) view.findViewById(R.id.contact_name);
			TextView messageView = (TextView) view.findViewById(R.id.message_sample);
			TextView timeView = (TextView) view.findViewById(R.id.message_time);
			ImageView imageView = (ImageView) view.findViewById(R.id.contact_picture);
			ImageView unreadIcon = (ImageView) view.findViewById(R.id.unread_icon);

			// retrieve data from cursor
			DatabaseHelper db = DatabaseHelper.getInstance(getActivity());
			long uid = cursor.getLong(cursor.getColumnIndex("uid"));
			Contact contact = db.getContact(uid);
			db.close();
			String name = contact.getNick();
			String message = cursor.getString(cursor.getColumnIndex(DatabaseHelper.KEY_MESSAGE));
			long timestamp = cursor.getLong(cursor.getColumnIndex("maxTimestamp"));
			long lastRead = cursor.getLong(cursor.getColumnIndex(DatabaseHelper.KEY_LAST_READ));

			if(lastRead < timestamp)
			{
				unreadIcon.setVisibility(ImageView.VISIBLE);
			}

			//format timestamp
			Date date = new Date(timestamp);
			SimpleDateFormat format = new SimpleDateFormat("HH:mm");

			// bind values
			nameView.setText(name);
			messageView.setText(message);
			timeView.setText(format.format(date));

			// load avatar
			String dir = getActivity().getFilesDir().toString();
			String file = contact.getUsername() + "_image";

			if(Arrays.asList(getActivity().fileList()).contains(file))
			{
				Bitmap savedImage = BitmapFactory.decodeFile(dir + "/" + file);
				imageView.setImageBitmap(savedImage);
			}
			else
			{
				imageView.setImageResource(R.drawable.default_contact_pic);
			}
		}
	}
}