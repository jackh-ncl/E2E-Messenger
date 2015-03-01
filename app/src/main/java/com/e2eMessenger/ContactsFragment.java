package com.e2eMessenger;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MergeCursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.preference.PreferenceManager;
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

import java.util.Arrays;

/**
 * Class that lists each of the user's stored contacts and current friend requests.
 *
 * <p>Clicking a contact opens up an activity displaying their information.</p>
 *
 * @author Jack Hindmarch
 */
public class ContactsFragment extends Fragment
{
	public static final String CONTACT_ID = "ContactsFragment.CONTACT_ID";

	private ContactsAdapter adapter;
	private ListView contactList;
	private BroadcastReceiver broadcastReceiver;

	private TextView noContacts;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setHasOptionsMenu(true);

		broadcastReceiver = new BroadcastReceiver()
		{
			@Override
			public void onReceive(Context context, Intent intent)
			{
				refreshAdapter();
			}
		};

		// If it is the first time the user is running the app, show a help dialog
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
		boolean addContactHelpDialogShown = prefs.getBoolean(SettingsActivity.ADD_CONTACT_HELP_DIALOG, false);
		System.out.println("help shown:" + addContactHelpDialogShown);

		if(!addContactHelpDialogShown)
		{
			showHelpDialog();

			// Set shown to true
			SharedPreferences.Editor editor = prefs.edit();
			editor.putBoolean(SettingsActivity.ADD_CONTACT_HELP_DIALOG, true);
			editor.apply();
		}
	}

	private void showHelpDialog()
	{
		String title = getString(R.string.addContactTitle);
		String body = getString(R.string.addContactBody);
		DialogHelper.showSimpleAlert(getActivity(), title, body);
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
	 * Reload the contact list from the database and put it in a new adapter.
	 *
	 * <p>Must be done this way to work, as simply setting the adapter's data does nothing.</p>
	 */
	public void refreshAdapter()
	{
		DatabaseHelper db = DatabaseHelper.getInstance(getActivity());
		MergeCursor contacts = new MergeCursor(db.getAllContacts());
		checkIfNoContacts(contacts);
		ContactsAdapter adapter = new ContactsAdapter(getActivity(), contacts);
		this.adapter = adapter;
		contactList.setAdapter(adapter);
	}

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, Bundle savedInstanceState)
	{
		final View rootView = inflater.inflate(R.layout.fragment_contacts, container, false);
		final DatabaseHelper db = DatabaseHelper.getInstance(getActivity());

		noContacts = (TextView) rootView.findViewById(R.id.no_contacts);

		// Initialise and fill up contact list
		contactList = (ListView) rootView.findViewById(R.id.contacts_list);
		MergeCursor contacts = new MergeCursor(db.getAllContacts());
		checkIfNoContacts(contacts);

		adapter = new ContactsAdapter(getActivity(), contacts);
		contactList.setAdapter(adapter);

		// Listen for click events on contact cards
		contactList.setOnItemClickListener(new AdapterView.OnItemClickListener()
		{
			@Override
			public void onItemClick(AdapterView<?> adapterView, View view, int i, long id)
			{
				Contact contact = db.getContact(id);
				if(contact.getStatus() == Contact.Status.NEW_REQUEST)
				{
					MainActivity mainActivity = (MainActivity) getActivity();
					mainActivity.acceptRequest(getActivity(), contact);
				}
				else
				{
					Intent intent = new Intent(getActivity(), ContactInfoActivity.class);
					intent.putExtra(CONTACT_ID, id);
					startActivity(intent);
				}
			}
		});

		return rootView;
	}

	/**
	 * Check to see if contact list is empty. If so, display "no contacts".
	 *
	 * @param cursor - cursor to check.
	 */
	public void checkIfNoContacts(Cursor cursor)
	{
		if(!cursor.moveToFirst())
		{
			noContacts.setVisibility(TextView.VISIBLE);
		}
		else
		{
			noContacts.setVisibility(TextView.GONE);
		}
	}

/*	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater)
	{
		// Inflate the menu; this adds items to the action bar if it is present.
		super.onCreateOptionsMenu(menu, inflater);
		inflater.inflate(R.menu.contacts, menu);
	}*/

/*	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch(item.getItemId())
		{
			case R.id.action_add_contact:
				addContact(getActivity(), null);
				return true;
			case R.id.action_settings:
				Intent intent = new Intent(getActivity(), SettingsActivity.class);
				startActivity(intent);
			default:
				return super.onOptionsItemSelected(item);

		}
	}*/




	/**
	 * Adapter that loads the card layout for each contact in the cursor.
	 */
	class ContactsAdapter extends CursorAdapter
	{
		private final LayoutInflater mInflater;

		public ContactsAdapter(Context context, Cursor cursor)
		{
			super(context, cursor, false);
			mInflater = LayoutInflater.from(context);
		}

		@Override
		public View newView(Context context, Cursor cursor, ViewGroup viewGroup)
		{
			return mInflater.inflate(R.layout.contacts_card, viewGroup, false);
		}

		@Override
		public void bindView(View view, Context context, Cursor cursor)
		{
			// Load data from database
			String nick = cursor.getString(cursor.getColumnIndex(DatabaseHelper.KEY_NICK));
			int index = cursor.getInt(cursor.getColumnIndex(DatabaseHelper.KEY_STATUS));
			Contact.Status status = Contact.Status.values()[index];

			// Bind data to views
			TextView nameView = (TextView) view.findViewById(R.id.contact_name_card);
			nameView.setText(nick);

			ImageView imageView = (ImageView) view.findViewById(R.id.contact_picture);
			String dir = getActivity().getFilesDir().toString();
			String file = cursor.getString(cursor.getColumnIndex(DatabaseHelper.KEY_USERNAME)) + "_image";

			if(Arrays.asList(getActivity().fileList()).contains(file))
			{
				Bitmap savedImage = BitmapFactory.decodeFile(dir + "/" + file);
				imageView.setImageBitmap(savedImage);
			}
			else
			{
				imageView.setImageResource(R.drawable.default_contact_pic);
			}

			TextView statusView = (TextView) view.findViewById(R.id.contact_status_card);
			ImageView asterisk = (ImageView) view.findViewById(R.id.new_request_icon);

			// Change the key status and colour depending on the contact's stored status
			switch(status)
			{
				case PENDING:
					statusView.setText("pending");
					statusView.setTextColor(getResources().getColor(R.color.dark_orange));
					break;
				case VALID:
					statusView.setText("valid");
					statusView.setTextColor(getResources().getColor(R.color.dark_green));
					break;
				case INVALID:
					statusView.setText("invalid");
					statusView.setTextColor(getResources().getColor(R.color.dark_red));
					break;
				case NEW_REQUEST:
					statusView.setText("new request");
					statusView.setTextColor(getResources().getColor(R.color.dark_blue));
					asterisk.setVisibility(ImageView.VISIBLE);
					break;
			}
		}
	}
}