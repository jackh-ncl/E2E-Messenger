package com.e2eMessenger;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.e2eMessenger.recaptcha.ReCaptcha;

/**
 * Registration activity opened if the user does not have a username stored in the phone.
 *
 * <p>It first registers the phone to the GCM servers to obtain its device ID, which can then
 * be sent to the app server along with the entered username to be stored together.</p>
 *
 * @author Jack Hindmarch
 */
public class RegistrationActivity extends ActionBarActivity
{
	public static Activity instance;

	public static final String TAG = "RegistrationActivity";
	private EditText usernameEntry;
	private EditText recaptchaEntry;
	private EditText passwordEntry;
	private ReCaptcha reCaptcha;
	private RelativeLayout loading;
	private TextView retry;

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		super.onCreateOptionsMenu(menu);
		getMenuInflater().inflate(R.menu.registration, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch(item.getItemId())
		{
			case R.id.help:
				showHelpDialog();
				return true;
			default:
				return super.onOptionsItemSelected(item);

		}
	}

	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_registration);

		instance = this;

		usernameEntry = (EditText) findViewById(R.id.username_entry);
		reCaptcha = (ReCaptcha) findViewById(R.id.recaptcha);
		recaptchaEntry = (EditText) findViewById(R.id.recaptcha_input);
		passwordEntry = (EditText) findViewById(R.id.password);
		loading = (RelativeLayout) findViewById(R.id.loading);
		retry = (TextView) findViewById(R.id.retry);

		retry.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				retry.setVisibility(TextView.GONE);
				refreshReCaptcha();
			}
		});

		TextView registerButton = (TextView) findViewById(R.id.register_button);

		refreshReCaptcha();

		final RegistrationHelper regHelper = new RegistrationHelper(this);
		final OnRegistrationError errorHandler = new OnRegistrationError()
		{
			@Override
			public void handleError(RegistrationHelper.ErrorType errorType)
			{
				if(errorType != RegistrationHelper.ErrorType.REAUTH)
				{
					refreshReCaptcha();
				}
			}
		};


		// If it is the first time the user is running the app, show a help dialog
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		boolean regHelpDialogShown = prefs.getBoolean(SettingsActivity.REG_HELP_DIALOG, false);

		if(!regHelpDialogShown)
		{
			showHelpDialog();

			// Set shown to true
			SharedPreferences.Editor editor = prefs.edit();
			editor.putBoolean(SettingsActivity.REG_HELP_DIALOG, true);
			editor.apply();
		}

		/**
		 * Check Google Play Services is installed. If not, alert user.
		 *
		 * <p>If it is, and they are not registered, then send info to server.</p>
		 */
		registerButton.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View view)
			{
				String username = usernameEntry.getText().toString().toLowerCase();
				String recaptchaInput = recaptchaEntry.getText().toString();
				String password = passwordEntry.getText().toString();
				String challengeKey = reCaptcha.getLastChallenge(Settings.RECAPTCHA_PUBLIC_KEY);

				if(Contact.isValidUsername(username) && isValidPassword(password) && isValidCaptcha(recaptchaInput))
				{
					// GooglePlayServices are installed on device
					if(regHelper.checkPlayServices()) // and username is valid
					{
						ProgressDialog progressDialog = ProgressDialog.show(RegistrationActivity.this, "Please wait", "Registering with server...", true);
						regHelper.registerInBackground(username, recaptchaInput, challengeKey, password, progressDialog, errorHandler);
					} else // No Play Services installed, should link to play store
					{
						Log.i(TAG, "No valid Google Play Services APK found.");
					}
				}
				else
				{
					// Show new toast
					CharSequence text = "Invalid details.";
					int duration = Toast.LENGTH_LONG;
					Toast toast = Toast.makeText(RegistrationActivity.this, text, duration);
					toast.show();
				}
			}
		});
	}

	public void refreshReCaptcha()
	{
		loading.setVisibility(RelativeLayout.VISIBLE);
		recaptchaEntry.setText("");

		reCaptcha.showChallengeAsync(Settings.RECAPTCHA_PUBLIC_KEY, new ReCaptcha.OnShowChallengeListener()
		{
			@Override
			public void onChallengeShown(boolean shown)
			{
				System.out.println("Challenge shown: " + shown);
				System.out.println("Challenge key: " + reCaptcha.getLastChallenge(Settings.RECAPTCHA_PUBLIC_KEY));

				loading.setVisibility(RelativeLayout.INVISIBLE);

				if(!shown)
				{
					// Show retry button
					retry.setVisibility(TextView.VISIBLE);
				}
			}
		});
	}

	private void showHelpDialog()
	{
		String title = getString(R.string.regHelpTitle);
		String body = getString(R.string.regHelpBody);
		DialogHelper.showSimpleAlert(this, title, body);
	}

	private boolean isValidPassword(String pass)
	{
		return pass.length() > 5 && pass.length() <= 64;
	}

	private boolean isValidCaptcha(String resp)
	{
		return resp.length() >=1 && resp.length() <= 64;
	}
}