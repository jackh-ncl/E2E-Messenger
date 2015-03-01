package com.e2eMessenger;

import android.content.Context;
import android.content.Intent;
import android.database.MergeCursor;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.support.v4.app.NavUtils;
import android.support.v4.widget.SimpleCursorAdapter;
import android.support.v7.app.ActionBarActivity;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;

/**
 * List activity which simply displays all of the user's stored contacts.
 *
 * @author Jack Hindmarch
 */
public class ContactListActivity extends ActionBarActivity
{
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_contact_list);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		super.onOptionsItemSelected(item);

		switch(item.getItemId())
		{
			case android.R.id.home:
				NavUtils.navigateUpFromSameTask(this);
				return true;
		}

		return true;
	}

	public static class ContactListFragment extends ListFragment
	{
		private Context context;

		public void onCreate(Bundle savedInstanceState)
		{
			super.onCreate(savedInstanceState);

			// Retrieve all stored contacts and put into adapter
			context = getActivity();
			DatabaseHelper db = DatabaseHelper.getInstance(context);
			MergeCursor contacts = new MergeCursor(db.getAllContacts());
			SimpleCursorAdapter adapter = new SimpleCursorAdapter(context, android.R.layout.simple_list_item_1, contacts, new String[]{DatabaseHelper.KEY_NICK}, new int[]{android.R.id.text1}, 0);

			setListAdapter(adapter);
		}

		/**
		 * Opens a conversation activity with whichever contact is selected.
		 */
		@Override
		public void onListItemClick(ListView l, View v, int position, long id)
		{
			Intent intent = new Intent(context, ConversationActivity.class);
			intent.putExtra("uid", id);
			startActivity(intent);
			((ActionBarActivity) context).finish();
		}
	}
}