package com.e2eMessenger;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;

/**
 * The main activity launched when the app is opened.
 *
 * <p>This is the app's home screen which provides a tabbed interface to view the user's
 * list of stored conversations and contacts.</p>
 *
 * @author Jack Hindmarch
 */
public class MainActivity extends ActionBarActivity implements ActionBar.TabListener
{
	private static final String TAG = "MainActivity";
	private final static int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;

	SectionsPagerAdapter mSectionsPagerAdapter;
	ViewPager mViewPager;

	private static int RESULT_LOAD_IMAGE = 1;

	private boolean unlocked = false;
	private Bitmap pickedImage;
	private ImageView imageView;

	/**
	 * Checks whether or not the user has a saved username or if the lock screen is enabled.
	 *
	 * <p>This determines whether or not to open the registration or lock screen, or continue
	 * to load the main activity.</p>
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		checkPlayServices();
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		boolean lockEnabled = prefs.getBoolean(SettingsActivity.LOCK_ENABLED, false);
		String storedPin = prefs.getString(SettingsActivity.PIN, "");
		String username = prefs.getString(SettingsActivity.USERNAME, "");
		String deviceID = prefs.getString(SettingsActivity.REG_ID, "");

/*		Bundle extras = getIntent().getExtras();
		if(extras != null)
		{
			unlocked = extras.getBoolean(LockScreenActivity.UNLOCKED, false);
		}*/

		if(username.isEmpty() || deviceID.isEmpty())
		{
			Intent intent = new Intent(this, RegistrationActivity.class);
			startActivity(intent);
			finish();
		}
		/*else if(!unlocked && lockEnabled && !storedPin.isEmpty())
		{
			Intent intent = new Intent(this, LockScreenActivity.class);
			intent.putExtra(GcmIntentService.NEW_CONTACT_REQUEST, newRequest);
			startActivity(intent);
			finish();
		}*/

		setContentView(R.layout.activity_main);

		// Set up the action bar.
		final ActionBar actionBar = getSupportActionBar();
		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

		// Create the adapter that will return a fragment for each of the three
		// primary sections of the app.
		mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

		// Set up the ViewPager with the sections adapter.
		mViewPager = (ViewPager) findViewById(R.id.pager);
		mViewPager.setAdapter(mSectionsPagerAdapter);

		// When swiping between different sections, select the corresponding
		// tab. We can also use ActionBar.Tab#select() to do this if we have
		// a reference to the Tab.
		mViewPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
			@Override
			public void onPageSelected(int position)
			{
				actionBar.setSelectedNavigationItem(position);
			}
		});

		// For each of the sections in the app, add a tab to the action bar.
		for (int i = 0; i < mSectionsPagerAdapter.getCount(); i++)
		{
			actionBar.addTab(
				actionBar.newTab()
					.setIcon(mSectionsPagerAdapter.getIcon(i))
					.setTabListener(this)
			);
		}

		// If no contacts, go to contacts tab
		DatabaseHelper db = DatabaseHelper.getInstance(this);
		int contactCount = db.getContactCount();

		if(contactCount == 0)
		{
			mViewPager.setCurrentItem(1);
		}

		checkCameFromNotification(getIntent());
	}

	private void showHelpDialog()
	{
		String title = getString(R.string.addContactTitle);
		String body = getString(R.string.addContactBody);
		DialogHelper.showSimpleAlert(this, title, body);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		super.onCreateOptionsMenu(menu);
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch(item.getItemId())
		{
			case R.id.action_new_conversation:
				startConversation();
				return true;
			case R.id.action_add_contact:
				addContact(this, null);
				return true;
			case R.id.help:
				showHelpDialog();
				return true;
			case R.id.action_settings:
				Intent intent = new Intent(this, SettingsActivity.class);
				startActivity(intent);
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	/**
	 * Opens a the contact list activity allowing user to specify a contact to speak with.
	 */
	public void startConversation()
	{
		DatabaseHelper db = DatabaseHelper.getInstance(this);
		int count = db.getContactCount();

		if(count > 0)
		{
			Intent intent = new Intent(this, ContactListActivity.class);
			startActivity(intent);
		}
		else
		{
			// Make a new toast
			CharSequence text = "You currently have no saved contacts.";
			int duration = Toast.LENGTH_LONG;
			Toast toast = Toast.makeText(this, text, duration);
			toast.show();
		}
	}

	public boolean contactIsBlocked(String username)
	{
		DatabaseHelper db = DatabaseHelper.getInstance(this);
		HashSet<String> blockedList = db.getBlockedUserHashSet();
		return blockedList.contains(username);
	}

	/**
	 * Display dialog that allows user to enter username and authentication password to use.
	 *
	 * <p>Also allows the selection of a display picture for the new contact.</p>
	 *
	 * <p>When 'Ok' is clicked then a new request is sent by executing round one of J-PAKE.</p>
	 */
	public void addContact(final Activity context, final String username) // username - if not null, send request to specified user
	{
		//final Activity context = getActivity();
		LayoutInflater inflater = context.getLayoutInflater();

		View layout = inflater.inflate(R.layout.fragment_add_contact, null);
		final EditText toUserView = (EditText) layout.findViewById(R.id.username);
		final EditText passwordView = (EditText) layout.findViewById(R.id.password);
		imageView = (ImageView) layout.findViewById(R.id.image);

		String title = "";

		if(username != null)
		{
			toUserView.setText(username);
			toUserView.setEnabled(false);
			title = "Request new key";

			// Load and set contact image
			String dir = context.getFilesDir().toString();
			String file = username + "_image";

			if(Arrays.asList(context.fileList()).contains(file))
			{
				Bitmap savedImage = BitmapFactory.decodeFile(dir + "/" + file);
				imageView.setImageBitmap(savedImage);
			}
			else
			{
				imageView.setImageResource(R.drawable.default_contact_pic);
			}
		}
		else // only allow image to be set when adding contact for the first time
		{
			title = "Add new contact";

			imageView.setOnClickListener(new View.OnClickListener()
			{
				@Override
				public void onClick(View view)
				{
					Intent in = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
					startActivityForResult(in, RESULT_LOAD_IMAGE);
				}
			});
		}

		// Build alert dialog
		new AlertDialog.Builder(context)
			.setTitle(title)
			.setView(layout)
			.setPositiveButton("Send", new DialogInterface.OnClickListener()
			{
				public void onClick(DialogInterface dialog, int whichButton)
				{
					String to = toUserView.getText().toString();
					String password = passwordView.getText().toString();

					SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
					String clientUsername = prefs.getString(SettingsActivity.USERNAME, "");

					if(!to.equals(clientUsername) && Contact.isValidUsername(to) && !password.isEmpty() && !contactIsBlocked(to))
					{
						JPAKE jpake = new JPAKE(context);
						jpake.aliceRound1(to, password);

						if(pickedImage != null)
						{
							saveImage(to, pickedImage);
						}

						// Only execute method if user is added from the main activity
						// (not from request new key in contact info activity)
						if(context == MainActivity.this) //if(mSectionsPagerAdapter != null)
						{
							mViewPager.setCurrentItem(1);
							ContactsFragment contactsFragment = (ContactsFragment) mSectionsPagerAdapter.getItem(1);
							if(contactsFragment.isResumed())
							{
								contactsFragment.refreshAdapter();
							}
						}

						// Broadcast data refresh
						Intent intent = new Intent("refresh");
						LocalBroadcastManager.getInstance(context).sendBroadcast(intent);

					} else
					{
						// Make a new toast
						CharSequence text = (contactIsBlocked(to)) ? "This username is blocked." : "Please enter a valid username and password.";
						int duration = Toast.LENGTH_LONG;
						Toast toast = Toast.makeText(context, text, duration);
						toast.show();
					}
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

	/**
	 * Dialog is opened if a new request contact is clicked.
	 *
	 * <p>Prompts user to enter the authentication password for the contact, and also
	 * allows the selection of a display picture.</p>
	 *
	 * @param contact - contact which was clicked.
	 */
	public void acceptRequest(Activity context, final Contact contact)
	{
		AlertDialog.Builder dialog = new AlertDialog.Builder(this);
		LayoutInflater inflater = context.getLayoutInflater();
		View layout = inflater.inflate(R.layout.fragment_add_contact, null);

		dialog.setView(layout);

		final EditText toUserView = (EditText) layout.findViewById(R.id.username);
		toUserView.setText(contact.getUsername());
		toUserView.setEnabled(false);
		final EditText passwordView = (EditText) layout.findViewById(R.id.password);

		// Build alert dialog
		dialog
			.setTitle("Request from " + contact.getUsername())
			.setPositiveButton("Accept", new DialogInterface.OnClickListener()
			{
				public void onClick(DialogInterface dialog, int whichButton)
				{
					String password = passwordView.getText().toString();

					if(!password.isEmpty())
					{
						JPAKE jpake = new JPAKE(MainActivity.this);
						jpake.bobRound1(contact.getUsername(), password);

						ContactsFragment contactsFragment = (ContactsFragment) mSectionsPagerAdapter.getItem(1);
						if(contactsFragment.isResumed())
						{
							contactsFragment.refreshAdapter();
						}
					}
					else
					{
						// Make a new toast
						CharSequence text = "Please enter a valid password.";
						int duration = Toast.LENGTH_LONG;
						Toast toast = Toast.makeText(MainActivity.this, text, duration);
						toast.show();
					}
				}
			})
			.setNeutralButton("Decline", new DialogInterface.OnClickListener()
			{
				public void onClick(DialogInterface dialog, int whichButton)
				{
					DatabaseHelper db = DatabaseHelper.getInstance(MainActivity.this);
					long uid = db.getContactID(contact.getUsername());

					if(db.getMessageCountForContact(uid) == 0)
					{
						db.deleteContact(uid);
					}
					else
					{
						contact.setStatus(Contact.Status.INVALID);
						contact.setStatusTime(System.currentTimeMillis());
						db.updateContact(contact);
					}

					// Alert "refresh" handler
					Intent intent = new Intent("refresh");
					LocalBroadcastManager.getInstance(MainActivity.this).sendBroadcast(intent);

					ContactsFragment contactsFragment = (ContactsFragment) mSectionsPagerAdapter.getItem(1);
					if(contactsFragment.isResumed())
					{
						contactsFragment.refreshAdapter();
					}
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

	/**
	 * Method which fetches the image that was selected by the user, and sets it to the
	 * displayed picture. Does not save to disk here in case the request is declined or canceled.
	 */
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		super.onActivityResult(requestCode, resultCode, data);
		System.out.println("result");

		if (requestCode == RESULT_LOAD_IMAGE && resultCode == Activity.RESULT_OK && data != null)
		{
			System.out.println("image picked");

			Uri selectedImage = data.getData();
			String[] filePathColumn = {MediaStore.Images.Media.DATA};
			Cursor cursor = getContentResolver().query(selectedImage, filePathColumn, null, null, null);
			cursor.moveToFirst();
			int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
			String picturePath = cursor.getString(columnIndex);
			cursor.close();
			Bitmap tempImg = (BitmapFactory.decodeFile(picturePath));
			pickedImage = Bitmap.createScaledBitmap(tempImg, 300, 300, false);
			imageView.setImageBitmap(pickedImage);
		}
	}

	/**
	 * Saves the image to disk using the contact's unique username.
	 *
	 * @param username - contacts username used in the filename.
	 * @param image - bitmap representation of the image.
	 */
	private void saveImage(String username, Bitmap image)
	{
		try
		{
			String filename = username + "_image";

			FileOutputStream fos = openFileOutput(filename, Context.MODE_PRIVATE);
			ByteArrayOutputStream stream = new ByteArrayOutputStream();
			image.compress(Bitmap.CompressFormat.PNG, 100, stream);
			byte[] bytes = stream.toByteArray();

			fos.write(bytes);
			fos.close();
		}
		catch(IOException e)
		{
			e.printStackTrace();
		}
	}

	/**
	 * If a new request has been received (from clicking notification of new request) then
	 * move the tab to view to the contacts list.
	 */
	@Override
	protected void onNewIntent(Intent intent)
	{
		super.onNewIntent(intent);

		checkCameFromNotification(intent);
	}

	private void checkCameFromNotification(Intent intent)
	{
		Bundle extras = intent.getExtras();
		if(extras != null)
		{
			boolean newRequest = extras.getBoolean(GcmIntentService.NEW_CONTACT_REQUEST, false);

			if(newRequest)
			{
				mViewPager.setCurrentItem(1);
			}
		}
	}

	@Override
	public void onTabSelected(ActionBar.Tab tab, android.support.v4.app.FragmentTransaction fragmentTransaction)
	{
		mViewPager.setCurrentItem(tab.getPosition());
	}

	@Override
	public void onTabUnselected(ActionBar.Tab tab, android.support.v4.app.FragmentTransaction fragmentTransaction)
	{

	}

	@Override
	public void onTabReselected(ActionBar.Tab tab, android.support.v4.app.FragmentTransaction fragmentTransaction)
	{

	}

	/**
	 * Check the device to make sure it has the Google Play Services APK. If it
	 * doesn't, display a dialog that allows users to download the APK from the
	 * Google Play Store or enable it in the device's system settings.
	 */
	private boolean checkPlayServices()
	{
		int resultCode = GooglePlayServicesUtil
			.isGooglePlayServicesAvailable(this);
		if (resultCode != ConnectionResult.SUCCESS)
		{
			if (GooglePlayServicesUtil.isUserRecoverableError(resultCode))
			{
				GooglePlayServicesUtil.getErrorDialog(resultCode, this, PLAY_SERVICES_RESOLUTION_REQUEST).show();
			} else
			{
				System.out.println("This device is not supported.");
				finish();
			}
			return false;
		}
		return true;
	}


	/**
	 * Holds the fragment data for each fragment in the view pager.
	 */
	public class SectionsPagerAdapter extends FragmentPagerAdapter
	{

		public SectionsPagerAdapter(FragmentManager fm) 
		{
			super(fm);
		}

		@Override
		public Fragment getItem(int position)
		{
			Fragment fragment = null;
			switch(position)
			{
				case 0:
					fragment = new ConversationsFragment();
					break;
				case 1:
					fragment = new ContactsFragment();
					break;
			}

			return fragment;
		}

		@Override
		public int getCount()
		{
			// Show 2 total pages.
			return 2;
		}

		@Override
		public CharSequence getPageTitle(int position)
		{
			Locale l = Locale.getDefault();
			switch(position)
			{
				case 0:
					return getString(R.string.title_section1).toUpperCase(l);
				case 1:
					return getString(R.string.title_section2).toUpperCase(l);
				case 2:
					return "debug".toUpperCase(l);
			}
				return null;
		}

		public Drawable getIcon(int position)
		{
			switch(position)
			{
				case 0:
					return getResources().getDrawable(R.drawable.ic_action_sms);
				case 1:
					return getResources().getDrawable(R.drawable.ic_action_users);
				case 2:
					return null;
			}
			return null;
		}
	}
}
