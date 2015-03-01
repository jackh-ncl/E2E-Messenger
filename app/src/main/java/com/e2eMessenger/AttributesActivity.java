package com.e2eMessenger;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.database.MatrixCursor;
import android.net.Uri;
import android.support.v4.app.ListFragment;
import android.support.v4.widget.SimpleCursorAdapter;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;


public class AttributesActivity extends ActionBarActivity
{
    @Override
    protected void onCreate(Bundle savedInstanceState)
	{
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_attributes);
    }

	public static class AttributesListFragment extends ListFragment
	{
		private Context context;

		@Override
		public void onCreate(Bundle savedInstanceState)
		{
			super.onCreate(savedInstanceState);
			context = this.getActivity();

			// Add required attributes to cursor
			String[] columns = new String[] { "_id", "attribute" };

			MatrixCursor attributes = new MatrixCursor(columns);
			attributes.addRow(new Object[] { 1, "Bouncy Castle" });
			attributes.addRow(new Object[] { 2, "J-PAKE" });
			attributes.addRow(new Object[] { 3, "Kryo" });

			// Set up a SimpleCursorAdapter
			SimpleCursorAdapter adapter = new SimpleCursorAdapter(context, android.R.layout.simple_list_item_1,
											attributes, new String[] { "attribute" }, new int[] { android.R.id.text1 }, 0);

			setListAdapter(adapter);
		}

		@Override
		public void onListItemClick(ListView l, View v, int position, long id)
		{
			switch(position)
			{
				case 0:
					createDialog(R.string.attr_bouncycastle, R.string.bouncycastle_notice);
					break;
				case 1:
					Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://eprint.iacr.org/2010/190.pdf"));
					startActivity(intent);
					break;
				case 2:
					createDialog(R.string.attr_kryo, R.string.kryo_notice);
					break;
			}
		}

		private void createDialog(int titleID, int noticeID)
		{
			String title = context.getString(titleID);
			String notice = context.getString(noticeID);

			new AlertDialog.Builder(context)
				.setTitle(title)
				.setMessage(notice)
				.show();
		}
	}
}
