package com.e2eMessenger;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

/**
 * Class for the lock screen activity that is opened if the user has the setting
 * enabled and has a pin code set.
 *
 * <p>Simply opens the main activity if the correct pin code is entered.</p>
 *
 * @author Jack Hindmarch
 */
public class LockScreenActivity extends Activity
{
	public static final String UNLOCKED = "com.e2eMessenger.UNLOCKED";
	boolean newRequest = false;

	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_lock_screen);

		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		final String storedPin = prefs.getString(SettingsActivity.PIN, "");

		Bundle extras = getIntent().getExtras();
		if(extras != null)
		{
			newRequest = extras.getBoolean(GcmIntentService.NEW_CONTACT_REQUEST, false);
		}


		final EditText pinEntry = (EditText) findViewById(R.id.pass_entry);
		TextView signInButton = (TextView) findViewById(R.id.lockscreen_signin);

		signInButton.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View view)
			{
				String pin = pinEntry.getText().toString();
				if(pin.equals(storedPin))
				{
					Intent intent = new Intent(LockScreenActivity.this, MainActivity.class);
					intent.putExtra(UNLOCKED, true);
					intent.putExtra(GcmIntentService.NEW_CONTACT_REQUEST, newRequest);
					startActivity(intent);
					finish();
				}
			}
		});
	}
}