package com.e2eMessenger;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.app.NavUtils;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.ActionBarActivity;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;

/**
 * This class adds functionality to the contact info activity. It allows
 * the user to view a contact's details, and perform actions such as delete,
 * edit and block.
 *
 * @author Jack Hindmarch
 */
public class ContactInfoActivity extends ActionBarActivity
{
	private TextView username;
	private TextView nick;
	private TextView statusView;
	private TextView statusTime;
	private ImageView image;
	private EditText editNick;
	private RelativeLayout contactButtons;
	private RelativeLayout changesButtons;
	private RelativeLayout saveChanges;
	private RelativeLayout discardChanges;
	private RelativeLayout editContact;
	private RelativeLayout blockContact;
	private RelativeLayout requestNewKey;
	private RelativeLayout removeContact;
	private RelativeLayout removeMessages;
	private RelativeLayout unblockContact;

	private boolean edit = false;
	private static int RESULT_LOAD_IMAGE = 1;
	private Bitmap pickedImage = null;

	private BroadcastReceiver broadcastReceiver;
	private Contact contact;

	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_contact_info);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);

		// Get contact ID and load details from database
		final long uid = getIntent().getLongExtra(ContactsFragment.CONTACT_ID, -1);
		DatabaseHelper db = DatabaseHelper.getInstance(this);
		contact = db.getContact(uid);
		db.close();

		// Load and set contact image if it exists.
		setImage();

		/* Bind views and set them to display contact info */

		username = (TextView) findViewById(R.id.ppl_username);
		username.setText(contact.getUsername());

		nick = (TextView) findViewById(R.id.ppl_nick);
		nick.setText(contact.getNick());

		statusView = (TextView) findViewById(R.id.ppl_status);
		statusTime = (TextView) findViewById(R.id.ppl_status_time);
		setStatusView();

		editNick = (EditText) findViewById(R.id.ppl_nick_edit);

		// Grouped buttons for hiding and showing if editing contact or not
		contactButtons = (RelativeLayout) findViewById(R.id.ppl_contact_buttons);
		changesButtons = (RelativeLayout) findViewById(R.id.ppl_changes_buttons);

		saveChanges = (RelativeLayout) findViewById(R.id.ppl_save_changes_layout);
		discardChanges = (RelativeLayout) findViewById(R.id.ppl_edit_cancel_layout);

		editContact = (RelativeLayout) findViewById(R.id.ppl_edit_layout);
		blockContact = (RelativeLayout) findViewById(R.id.ppl_block_layout);
		requestNewKey = (RelativeLayout) findViewById(R.id.ppl_new_key_layout);
		removeContact = (RelativeLayout) findViewById(R.id.ppl_remove_layout);
		removeMessages = (RelativeLayout) findViewById(R.id.ppl_rmvmsg_layout);
		unblockContact = (RelativeLayout) findViewById(R.id.ppl_unblock_layout);

		// Check if user is blocked
		HashSet<String> blockedList = db.getBlockedUserHashSet();

		if(blockedList.contains(contact.getUsername()))
		{
			blockContact();
		}

		/**
		 * Set activity to 'edit mode', allowing user to edit the
		 * contact's nickname and change the display picture.
		 *
		 * <p>Hides deletion buttons and shows save/discard buttons.</p>
		 */
		editContact.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View view)
			{
				edit = true;
				nick.setVisibility(TextView.GONE);
				editNick.setText(contact.getNick());
				editNick.setVisibility(TextView.VISIBLE);

				contactButtons.setVisibility(RelativeLayout.GONE);
				changesButtons.setVisibility(RelativeLayout.VISIBLE);
			}
		});


		/**
		 * Saves whatever changes the user made when in 'edit mode', persists them and
		 * then returns to non-edit mode.
		 */
		saveChanges.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View view)
			{
				edit = false;
				// CHECK FOR NON-EMPTY / UNIQUENESS
				// Save new nick to database
				DatabaseHelper db = DatabaseHelper.getInstance(ContactInfoActivity.this);
				EditText newNick = (EditText) findViewById(R.id.ppl_nick_edit);
				String newNickStr = newNick.getText().toString();

				if(!newNickStr.isEmpty())
				{
					contact.setNick(newNickStr);
					db.updateContact(contact);
					nick.setText(newNickStr);
				}
				else
				{
					// Make a new toast
					CharSequence text = "Nick must not be blank.";
					int duration = Toast.LENGTH_LONG;
					Toast toast = Toast.makeText(ContactInfoActivity.this, text, duration);
					toast.show();
				}

				// save image
				try
				{
					if(pickedImage != null)
					{
						String filename = contact.getUsername() + "_image";
						FileOutputStream fos = openFileOutput(filename, Context.MODE_PRIVATE);

						Bitmap scaled = Bitmap.createScaledBitmap(pickedImage, 300, 300, false);
						ByteArrayOutputStream stream = new ByteArrayOutputStream();
						scaled.compress(Bitmap.CompressFormat.PNG, 100, stream);
						byte[] bytes = stream.toByteArray();

						fos.write(bytes);
						fos.close();
					}
				}
				catch(FileNotFoundException e)
				{
					e.printStackTrace();
				} catch(IOException e)
				{
					e.printStackTrace();
				}

				// Change views back to original settings
				nick.setVisibility(TextView.VISIBLE);
				editNick.setVisibility(TextView.GONE);
				changesButtons.setVisibility(RelativeLayout.GONE);
				contactButtons.setVisibility(RelativeLayout.VISIBLE);
			}
		});

		/**
		 * Discards whatever changes the user made and then returns to non-edit mode.
		 */
		discardChanges.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View view)
			{
				edit = false;
				// Change views back to original settings
				setImage();
				nick.setVisibility(TextView.VISIBLE);
				editNick.setVisibility(TextView.GONE);
				changesButtons.setVisibility(RelativeLayout.GONE);
				contactButtons.setVisibility(RelativeLayout.VISIBLE);
			}
		});

		/**
		 * Opens image picker that allows user to select a display picture for the contact
		 * from their device storage.
		 */
		image.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View view)
			{
				if (edit)
				{
					Intent in = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
					startActivityForResult(in, RESULT_LOAD_IMAGE);
				}
			}
		});


		requestNewKey.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				MainActivity mainActivity = new MainActivity();
				mainActivity.addContact(ContactInfoActivity.this, contact.getUsername());
			}
		});


		blockContact.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				// Build confirmation dialog
				new AlertDialog.Builder(ContactInfoActivity.this)
					.setTitle("Block contact")
					.setMessage("Are you sure you wish to block this contact?\n\n" +
							"This will result in their encryption key being invalidated, which " +
							"means that you will no longer be able to message this user, and a fresh" +
							" key exchange must be performed if you wish to contact them user again.")
					.setPositiveButton("Ok", new DialogInterface.OnClickListener()
					{
						public void onClick(DialogInterface dialog, int whichButton)
						{
							DatabaseHelper db = DatabaseHelper.getInstance(ContactInfoActivity.this);

							// Update contact
							db.insertBlockedUser(contact.getUsername());
							contact.setStatus(Contact.Status.INVALID);
							contact.setStatusTime(System.currentTimeMillis());
							db.updateContact(contact);

							// Alert data refresh
							Intent intent = new Intent("refresh");
							LocalBroadcastManager.getInstance(ContactInfoActivity.this).sendBroadcast(intent);

							blockContact();
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
		});

		unblockContact.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				DatabaseHelper db = DatabaseHelper.getInstance(ContactInfoActivity.this);
				db.removeBlockedUser(contact.getUsername());
				unblockContact();
			}
		});

		/**
		 * Presents user with warning dialog.
		 *
		 * <p>If they click 'Ok' then remove all messages for the contact from the database.</p>
		 */
		removeMessages.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View view)
			{
				new AlertDialog.Builder(ContactInfoActivity.this)
					.setTitle("Warning")
					.setMessage("Delete all messages for " + contact.getNick() + "?")
					.setPositiveButton("Ok", new DialogInterface.OnClickListener()
					{
						@Override
						public void onClick(DialogInterface dialogInterface, int i)
						{
							// Run db operations
							DatabaseHelper db = DatabaseHelper.getInstance(ContactInfoActivity.this);
							db.deleteMessages(uid);
							db.close();

							// Alert "refresh" handler
							Intent intent = new Intent("refresh");
							LocalBroadcastManager.getInstance(ContactInfoActivity.this).sendBroadcast(intent);
						}
					})
					.setNegativeButton("Cancel", new DialogInterface.OnClickListener()
					{
						@Override
						public void onClick(DialogInterface dialogInterface, int i)
						{
							// Do nothing
						}
					})
					.show();
			}
		});

		/**
		 * Presents user with warning dialog.
		 *
		 * <p>If they click 'Ok' then remove the contact from the database.</p>
		 */
		removeContact.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View view)
			{

				new AlertDialog.Builder(ContactInfoActivity.this)
					.setTitle("Warning")
					.setMessage("Remove " + contact.getNick() + " from your contact list?")
					.setPositiveButton("Ok", new DialogInterface.OnClickListener()
					{
						@Override
						public void onClick(DialogInterface dialogInterface, int i)
						{
							// Run db operations
							DatabaseHelper db = DatabaseHelper.getInstance(ContactInfoActivity.this);
							db.deleteContact(uid);
							db.close();

							// Alert "refresh" handler
							Intent intent = new Intent("refresh");
							LocalBroadcastManager.getInstance(ContactInfoActivity.this).sendBroadcast(intent);

							// delete avatar
							File file = new File(getFilesDir(), contact.getUsername() + "_image");
							file.delete();

							finish();
						}
					})
					.setNegativeButton("Cancel", new DialogInterface.OnClickListener()
					{
						@Override
						public void onClick(DialogInterface dialogInterface, int i)
						{
							// Do nothing
						}
					})
					.show();
			}
		});

		/**
		 * Broadcast receiver which refreshes the displayed data if alerted.
		 */
		broadcastReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				DatabaseHelper db = DatabaseHelper.getInstance(ContactInfoActivity.this);
				contact = db.getContact(uid);
				if(contact != null)
				{
					setStatusView();
				}
			}
		};
	}

	private void blockContact()
	{
		requestNewKey.setVisibility(RelativeLayout.INVISIBLE);
		blockContact.setVisibility(RelativeLayout.INVISIBLE);
		unblockContact.setVisibility(RelativeLayout.VISIBLE);


		RelativeLayout.LayoutParams p = (RelativeLayout.LayoutParams) removeMessages.getLayoutParams();

		System.out.println(p.leftMargin);
		System.out.println(p.getRules().length);

		p.addRule(RelativeLayout.BELOW, R.id.ppl_unblock_layout);

		removeMessages.setLayoutParams(p);
	}

	private void unblockContact()
	{
		requestNewKey.setVisibility(RelativeLayout.VISIBLE);
		blockContact.setVisibility(RelativeLayout.VISIBLE);
		unblockContact.setVisibility(RelativeLayout.INVISIBLE);


		RelativeLayout.LayoutParams p = (RelativeLayout.LayoutParams) removeMessages.getLayoutParams();

		p.addRule(RelativeLayout.BELOW, R.id.ppl_block_layout);

		removeMessages.setLayoutParams(p);
	}

	/**
	 * Loads the contact's stored image and displays.
	 */
	private void setImage()
	{
		// Load image file
		image = (ImageView) findViewById(R.id.ppl_picture);
		String dir = getFilesDir().toString();
		String file = contact.getUsername() + "_image";

		if(Arrays.asList(fileList()).contains(file))
		{
			Bitmap savedImage = BitmapFactory.decodeFile(dir + "/" + file);
			image.setImageBitmap(savedImage);
		}
		else
		{
			image.setImageResource(R.drawable.default_contact_pic);
		}
	}

	/**
	 * Sets the statusView view depending on the contact's current state.
	 */
	private void setStatusView()
	{
		long timestamp = contact.getStatusTime();
		Date date = new Date(timestamp);
		SimpleDateFormat timeFormat = new SimpleDateFormat("dd/MM/yyyy, HH:mm");

		// Set statusView/statusTime depending on contact statusView
		Contact.Status status = contact.getStatus();
		switch(status)
		{
			case PENDING:
				statusView.setText("Pending since:");
				statusTime.setText(timeFormat.format(date));
				statusTime.setTextColor(getResources().getColor(R.color.dark_orange));
				break;
			case VALID:
				statusView.setText("Valid since:");
				statusTime.setText(timeFormat.format(date));
				statusTime.setTextColor(getResources().getColor(R.color.dark_green));
				break;
			case INVALID:
				statusView.setText("Invalid since:");
				statusTime.setText(timeFormat.format(date));
				statusTime.setTextColor(getResources().getColor(R.color.dark_red));
				break;
		}
	}

	/**
	 * Method which fetches the image that was selected by the user, and sets it to the
	 * displayed picture. Does not save to disk here in case user wishes to discard
	 * changes.
	 */
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		super.onActivityResult(requestCode, resultCode, data);

		if (requestCode == RESULT_LOAD_IMAGE && resultCode == RESULT_OK && data != null)
		{
			Uri selectedImage = data.getData();
			String[] filePathColumn = {MediaStore.Images.Media.DATA};
			Cursor cursor = getContentResolver().query(selectedImage, filePathColumn, null, null, null);
			cursor.moveToFirst();
			int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
			String picturePath = cursor.getString(columnIndex);
			cursor.close();
			Bitmap tempImg = (BitmapFactory.decodeFile(picturePath));
			pickedImage = Bitmap.createScaledBitmap(tempImg, 300, 300, false);
			image.setImageBitmap(pickedImage);
		}
	}

	@Override
	public void onResume()
	{
		super.onResume();
		LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, new IntentFilter("refresh"));
	}

	@Override
	public void onPause()
	{
		super.onPause();
		LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver);
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
}