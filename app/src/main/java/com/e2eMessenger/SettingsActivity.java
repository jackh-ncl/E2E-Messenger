package com.e2eMessenger;

import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.support.v4.app.NavUtils;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.preference.PreferenceFragment;
import android.support.v7.app.ActionBarActivity;
import android.view.MenuItem;
import android.widget.Toast;


/**
 * Settings activity which loads the XML preferences file.
 *
 * <p>The class doesn't really do anything except this, but it also holds the preference
 * keys for easy access anywhere in the codebase.</p>
 *
 * @author Jack Hindmach
 */
public class SettingsActivity extends ActionBarActivity
{
	public static final String NOTIFICATIONS = "notifications";
	public static final String NOTIFICATIONS_RINGTONE = "notifications_ringtone";
	public static final String NOTIFICATIONS_VIBRATE = "notifications_vibrate";
	public static final String RECEIVE_REQUESTS = "receive_requests";
	public static final String LOCK_ENABLED = "lockscreen_on";
	public static final String PIN = "lockscreen_pin";

	public static final String USERNAME = "username";
	public static final String SESSION_KEY = "session_key";
	public static final String REG_ID = "registration_id";
	public static final String REAUTHORISING = "reauthorising";
	public static final String PROPERTY_APP_VERSION = "appVersion";

	// First time running the app alert dialog flags
	public static final String REG_HELP_DIALOG = "regHelpDialogShown";
	public static final String ADD_CONTACT_HELP_DIALOG = "addContactHelpDialogShown";

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_settings);
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

	public static class SettingsFragment extends PreferenceFragment
	{
		private Preference usernamePref;
		private BroadcastReceiver broadcastReceiver;
		private Context context;

		@Override
		public void onCreate(Bundle savedInstanceState)
		{
			super.onCreate(savedInstanceState);
			addPreferencesFromResource(R.xml.preferences);

			context = getActivity();
			//getActionBar().setDisplayHomeAsUpEnabled(true);

			// Display summary for 'set username'
			usernamePref = findPreference(USERNAME);
			updateUsernameSummary();

			usernamePref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
			{
				@Override
				public boolean onPreferenceClick(Preference preference)
				{
					RegistrationDialog dialog = new RegistrationDialog(context);
					dialog.showMe(false, false);
					return true;
				}
			});

			/*usernamePref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener()
			{
				@Override
				public boolean onPreferenceChange(Preference preference, Object newValue)
				{
					String newUsername = (String) newValue;
					System.out.println(newUsername);

					if(Contact.isValidUsername(newUsername)) // is valid
					{
						ProgressDialog progressDialog = ProgressDialog.show(context, "Please wait", "Registering with server...", true);
						RegistrationHelper reg = new RegistrationHelper(context);
						reg.registerInBackground(newUsername, "", "", "", progressDialog);
					} else
					{
						// Show new toast
						CharSequence text = "Invalid username.";
						int duration = Toast.LENGTH_LONG;
						Toast toast = Toast.makeText(context, text, duration);
						toast.show();
					}

					return false;
				}
			});*/

			broadcastReceiver = new BroadcastReceiver()
			{
				@Override
				public void onReceive(Context context, Intent intent)
				{
					updateUsernameSummary();
				}
			};
		}

		private void updateUsernameSummary()
		{
			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
			String username = prefs.getString(USERNAME, "");
			usernamePref.setSummary("You are currently registered as: " + username);
		}

		@Override
		public void onResume()
		{
			super.onResume();
			LocalBroadcastManager.getInstance(context).registerReceiver(broadcastReceiver, new IntentFilter("refresh"));
		}

		@Override
		public void onPause()
		{
			super.onPause();
			LocalBroadcastManager.getInstance(context).unregisterReceiver(broadcastReceiver);
		}

		/*@Override
		public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
		{
/*		// Change username setting
		if(key.equals(USERNAME))
		{
			EditTextPreference pref = (EditTextPreference) findPreference(key);
			String username = pref.getText();
			System.out.println(username);

			// if username is valid
				RegistrationHelper reg = new RegistrationHelper(this);
				reg.registerInBackground(username);

				// If successful
					// Set all keys to invalid
					DatabaseHelper db = DatabaseHelper.getInstance(this);
					db.invalidateAllKeys();
		}
		}*/

	}
}