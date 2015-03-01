package com.e2eMessenger;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.ListFragment;
import android.support.v4.app.NavUtils;
import android.support.v4.widget.SimpleCursorAdapter;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

/**
 * Created by jack on 08/09/2014.
 */
public class BlockedUserListActivity extends ActionBarActivity
{
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_blocked_user);
	}

	public static class BlockedUserListFragment extends ListFragment
	{
		private Context context;

		@Override public void onCreate(Bundle savedInstanceState)
		{
			super.onCreate(savedInstanceState);
			context = getActivity();

			refreshAdapter();
		}

		@Override
		public void onActivityCreated(Bundle savedInstanceState)
		{
			super.onActivityCreated(savedInstanceState);
			setHasOptionsMenu(true);
		}

		@Override
		public void onCreateOptionsMenu(Menu menu, MenuInflater inflater)
		{
			super.onCreateOptionsMenu(menu, inflater);
			inflater.inflate(R.menu.blocked_user, menu);
		}

		public void refreshAdapter()
		{
			DatabaseHelper db = DatabaseHelper.getInstance(context);
			Cursor users = db.getBlockedUsers();
			SimpleCursorAdapter adapter = new SimpleCursorAdapter(context, android.R.layout.simple_list_item_1,
											users, new String[]{DatabaseHelper.KEY_USERNAME}, new int[]{android.R.id.text1}, 0);

			setListAdapter(adapter);
		}

		@Override
		public void onListItemClick(ListView l, View v, int position, final long id)
		{
			super.onListItemClick(l, v, position, id);
			final DatabaseHelper db = DatabaseHelper.getInstance(context);
			String username = db.getBlockedUser(id);

			// Build alert dialog
			new AlertDialog.Builder(context)
				.setTitle("Unblock User")
				.setMessage("Unblock " + username + "?\n\n(user will now be able to send key requests and messages).")
				.setPositiveButton("Ok", new DialogInterface.OnClickListener()
				{
					public void onClick(DialogInterface dialog, int whichButton)
					{
						db.removeBlockedUser(id);
						refreshAdapter();
					}
				})
				.setNegativeButton("Cancel", new DialogInterface.OnClickListener()
				{
					public void onClick(DialogInterface dialog, int whichButton)
					{
						// Do nothing.
					}
				}).show();
		}
		@Override
		public boolean onOptionsItemSelected(MenuItem item)
		{
			super.onOptionsItemSelected(item);

			switch(item.getItemId())
			{
				case android.R.id.home:
					NavUtils.navigateUpFromSameTask((Activity) context);
					return true;
				case R.id.action_block_user:
					blockUser();
					return true;
				default:
					return false;
			}
		}

		private void blockUser()
		{
			// Build alert dialog
			final EditText input = new EditText(context);
			input.setHint("Enter a username");

			new AlertDialog.Builder(context)
				.setTitle("Block User")
				.setView(input)
				.setPositiveButton("Add", new DialogInterface.OnClickListener()
				{
					public void onClick(DialogInterface dialog, int whichButton)
					{
						// Input validation - write a Contact.isValidUsername() function?
						String username = input.getText().toString();

						// Invalidate key if user exists
						DatabaseHelper db = DatabaseHelper.getInstance(context);
						long uid = db.getContactID(username);

						SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
						String clientUsername = prefs.getString(SettingsActivity.USERNAME, "");

						if(uid != -1)
						{
							confirmBlock(uid);
						} else if(Contact.isValidUsername(username) && !username.equals(clientUsername))
						{
							// Add username to db
							db.insertBlockedUser(username);
						} else
						{
							// Show toast
							CharSequence text = "Invalid username.";
							int duration = Toast.LENGTH_LONG;
							Toast toast = Toast.makeText(context, text, duration);
							toast.show();
						}

						// Refresh list
						refreshAdapter();
					}
				})
				.setNegativeButton("Cancel", new DialogInterface.OnClickListener()
				{
					public void onClick(DialogInterface dialog, int whichButton)
					{
						// Do nothing.
					}
				})
				.show();
		}

		private void confirmBlock(final long uid)
		{
			// Build confirmation dialog
			new AlertDialog.Builder(context)
				.setTitle("Block contact")
				.setMessage("Are you sure you wish to block this contact?\n\n" +
							"This will result in their encryption key being invalidated, which " +
							"means that you will no longer be able to message this user, and a fresh " +
							"key exchange must be performed if you wish to contact them user again.")
				.setPositiveButton("Ok", new DialogInterface.OnClickListener()
				{
					public void onClick(DialogInterface dialog, int whichButton)
					{
						DatabaseHelper db = DatabaseHelper.getInstance(context);

						Contact contact = db.getContact(uid);
						contact.setStatus(Contact.Status.INVALID);
						contact.setStatusTime(System.currentTimeMillis());
						db.updateContact(contact);

						db.insertBlockedUser(contact.getUsername());

						// Refresh list
						refreshAdapter();
					}
				})
				.setNegativeButton("Cancel", new DialogInterface.OnClickListener()
			{
				public void onClick(DialogInterface dialog, int whichButton)
				{
					// Do nothing.
				}
			})
				.show();
		}
	}
}

