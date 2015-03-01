package com.e2eMessenger;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.e2eMessenger.Message.MessageType;
import com.e2eMessenger.recaptcha.ReCaptcha;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.gcm.GoogleCloudMessaging;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

/**
 * Created by Jack on 04/06/2014.
 */
public class RegistrationHelper
{
	public static final String TAG = "RegistrationHelper";
	public Context context;
	private final static int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;
	private static final String SENDER_ID = "YOUR_GOOGLE_API_PROJECT_NUMBER";

	public RegistrationHelper(Context context)
	{
		this.context = context;
	}

	public enum ErrorType {REAUTH, EXISTS, VALIDATION, RECAPTCHA, PASSWORD}

	/**
	 * Registers the application with GCM servers asynchronously and returns the generated
	 * device ID.
	 */
	public String registerGCM()
	{
		String regid = "";

		try
		{
			GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(context);
			regid = gcm.register(SENDER_ID);

		} catch(IOException e)
		{
			e.printStackTrace();
		}

		return regid;
	}

	/**
	 * Stores the registration ID and app versionCode in the application's
	 * shared preferences.
	 */
	@SuppressWarnings("unchecked")
	public void registerInBackground(final String username, final String recaptchaInput,
						 final String challengeKey, final String password, final ProgressDialog dialog,
						 final OnRegistrationError errorHandler)
	{
		new AsyncTask<Void, Void, JSONObject>() {

			Exception e;
			String regid;
			ProgressDialog progressDialog;

			protected JSONObject doInBackground(Void... params)
			{
				this.progressDialog = dialog;
				JSONObject resp = null;

				try
				{
					// Get registration ID
					regid = getRegistrationId();

					if(regid.isEmpty())
					{
						GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(context);
						regid = gcm.register(SENDER_ID);
					}

					// Get session key (if exists)
					SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
					String session_key = prefs.getString(SettingsActivity.SESSION_KEY, "");
					System.out.println("Session key: " + session_key);

					// Register device with server
					JSONObject data = new JSONObject();
					data.put("username", username);
					data.put("device_id", regid);
					data.put("session_key", session_key);
					data.put("recaptcha", recaptchaInput);
					data.put("challenge_key", challengeKey);
					data.put("password", password);

					URL url = new URL(Settings.SERVER_URL + "register");
					HttpURLConnection request = (HttpURLConnection) url.openConnection();
					request.setDoOutput(true); //POST
					request.setRequestProperty("Content-Type", "application/json");
					request.setConnectTimeout(30000);

					OutputStreamWriter writer = new OutputStreamWriter(request.getOutputStream());
					writer.write(data.toString());
					writer.flush();
					writer.close();
					InputStream is = request.getInputStream();
					Scanner scanner = new Scanner(is);
					String respStr = (scanner.hasNextLine()) ? scanner.nextLine() : "";

					System.out.println(respStr);
					resp = new JSONObject(respStr);

					if(resp.getInt("success") == 1)
					{
						System.out.println("Registration successful");

						System.out.println("Device registered, registration ID=" + regid + " " + resp);
						storeRegistrationId(username, regid);

						SharedPreferences.Editor editor = prefs.edit();
						editor.putString(SettingsActivity.USERNAME, username);
						editor.putString(SettingsActivity.SESSION_KEY, resp.getString("session_key"));
						editor.commit();

						// Invalidate all existing keys
						DatabaseHelper db = DatabaseHelper.getInstance(context);
						db.invalidateAllKeys();

						// Broadcast success
						Intent intent = new Intent("refresh");
						LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
					}
				}
				catch (IOException ex)
				{
					this.e = ex;
					System.out.println("Registration message not sent.");
					e.printStackTrace();
				} catch (JSONException e)
				{
					e.printStackTrace();
					this.e = e;
				} catch(Exception e)
				{
					this.e = e;
				}

				return resp;
			}

			protected void onPostExecute(JSONObject resp)
			{
				if(progressDialog != null)
				{
					this.progressDialog.dismiss();
				}

				// Device error handling
				try {
					if(resp != null && resp.getInt("success") == 1)
					{
						System.out.println("No errors!");
						System.out.println(context.getClass().getName());

						// Make a new toast
						CharSequence text = "Successfully registered.";
						int duration = Toast.LENGTH_LONG;
						Toast toast = Toast.makeText(context, text, duration);
						toast.show();

						if(context instanceof RegistrationActivity || context instanceof GcmIntentService)
						{
							Intent intent = new Intent(context, MainActivity.class);
							intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
							context.startActivity(intent);

							// Close registration activity
							if(RegistrationActivity.instance != null)
							{
								RegistrationActivity.instance.finish();
							}
						}
					}
					else if(e instanceof IOException)
					{
						// Make a new toast
						CharSequence text = "Cannot communicate with server.";
						int duration = Toast.LENGTH_LONG;
						Toast toast = Toast.makeText(context, text, duration);
						toast.show();
					}
					else if(e instanceof JSONException)
					{
						// Make a new toast
						CharSequence text = "Server response error.";
						int duration = Toast.LENGTH_LONG;
						Toast toast = Toast.makeText(context, text, duration);
						toast.show();
					}
					else if(e != null) // Just in case I've forgotten about any other type of exception that may arise
					{
						// Make a new toast
						CharSequence text = "Error registering with server.";
						int duration = Toast.LENGTH_LONG;
						Toast toast = Toast.makeText(context, text, duration);
						toast.show();
					}
					else if(resp != null && resp.getInt("success") == 0) // server error handling
					{
						int ordinal = resp.getInt("error_type");
						ErrorType errorType = ErrorType.values()[ordinal];

						if(errorHandler != null)
						{
							errorHandler.handleError(errorType);
						}

						switch(errorType)
						{
							case REAUTH:
								reauthorise();
								break;
							case EXISTS:
								exists();
								break;
							case VALIDATION:
								validationError();
								break;
							case RECAPTCHA:
								reCaptchaError();
								break;
							case PASSWORD:
								passwordError();
								break;
						}
					}
				} catch(JSONException e) {
					e.printStackTrace();
				}
			}

			/*protected void showWaitingDialog()
			{
				LinearLayout layout = new LinearLayout(context);
				ImageView loadBar = (ImageView) android.R.drawable.wa

				new AlertDialog.Builder(context)
					.setTitle("Please wait")
					.setView()
					.setCancelable(false)
					.show();
			}*/

			/***********************************************************************
			 /* Error functions */

			protected void reauthorise()
			{
				// Make a new toast
				CharSequence text = "Reauthorising...";
				int duration = Toast.LENGTH_LONG;
				Toast toast = Toast.makeText(context, text, duration);
				toast.show();

				// Set flag to reauthorise
				SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
				SharedPreferences.Editor editor = prefs.edit();
				editor.putBoolean(SettingsActivity.REAUTHORISING, true);
				editor.apply();

				/*new AsyncTask<Void, Void, Boolean>() {
					@Override
					protected Boolean doInBackground(Void... params)
					{
						try {
							// wait ten seconds and check if user
							// reauthorised successfully.
							Thread.sleep(10000);

							SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
							Boolean stillReauthorising = prefs.getBoolean(SettingsActivity.REAUTHORISING, true);

							// set reauthorising back to false
							if(stillReauthorising)
							{
								SharedPreferences.Editor editor = prefs.edit();
								editor.putBoolean(SettingsActivity.REAUTHORISING, false);
								editor.apply();
							}

							return stillReauthorising;

						} catch(InterruptedException e)
						{
							e.printStackTrace();
							return false;
						}
					}

					@Override
					protected void onPostExecute(Boolean stillReauthorising)
					{
						super.onPostExecute(stillReauthorising);

						if(stillReauthorising)
						{
							// Make a new toast
							CharSequence text = "Reauthorisation attempt timed out.";
							int duration = Toast.LENGTH_LONG;
							Toast toast = Toast.makeText(context, text, duration);
							toast.show();
						}
					}
				}.execute();*/

				try {
					JSONObject req = new JSONObject();
					req.put("username", username);
					req.put("device_id", regid);
					req.put("message_type", MessageType.REAUTH.ordinal());
					req.put("register", true);

					Message.send(req, MessageType.REAUTH, null, context);

				} catch(JSONException e) {
					e.printStackTrace();
				}
			}

			protected void exists()
			{
				// Make a new toast
				CharSequence text = "This username is already registered and is password protected.";
				int duration = Toast.LENGTH_LONG;
				Toast toast = Toast.makeText(context, text, duration);
				toast.show();

				showRegistrationDialog(context, true, true);
			}

			protected void validationError()
			{
				// Make a new toast
				CharSequence text = "Invalid registration details.";
				int duration = Toast.LENGTH_LONG;
				Toast toast = Toast.makeText(context, text, duration);
				toast.show();

				showRegistrationDialog(context, true, true);
			}

			protected void reCaptchaError()
			{
				// Make a new toast
				CharSequence text = "Incorrect CAPTCHA.";
				int duration = Toast.LENGTH_LONG;
				Toast toast = Toast.makeText(context, text, duration);
				toast.show();

				if(context instanceof RegistrationActivity)
				{
					RegistrationActivity a = (RegistrationActivity) context;
					a.refreshReCaptcha();
				}

				showRegistrationDialog(context, true, true);
			}

			protected void passwordError()
			{
				// Make a new toast
				CharSequence text = "Invalid password.";
				int duration = Toast.LENGTH_LONG;
				Toast toast = Toast.makeText(context, text, duration);
				toast.show();

				showRegistrationDialog(context, true, true);
			}

		}.execute(null, null, null);
	}

	public void refreshReCaptcha(View layout)
	{
		final RelativeLayout loading = (RelativeLayout) layout.findViewById(R.id.loading);
		loading.setVisibility(RelativeLayout.VISIBLE);

		ReCaptcha reCaptcha = (ReCaptcha) layout.findViewById(R.id.recaptcha);

		EditText reCaptchaEntry = (EditText) layout.findViewById(R.id.recaptcha_input);
		reCaptchaEntry.setText("");

		reCaptcha.showChallengeAsync(Settings.RECAPTCHA_PUBLIC_KEY, new ReCaptcha.OnShowChallengeListener()
		{
			@Override
			public void onChallengeShown(boolean shown)
			{
				System.out.println("Challenge shown: " + shown);
				loading.setVisibility(RelativeLayout.INVISIBLE);
			}
		});
	}

	public void showRegistrationDialog(Context context, boolean requirePassword, boolean requireCaptcha)
	{
		// For any context other than registration activity,
		// prompt user with another registration dialog.
		if(!(context instanceof RegistrationActivity) && !(context instanceof GcmIntentService))
		{
			RegistrationDialog dialog = new RegistrationDialog(context);
			dialog.showMe(requirePassword, requireCaptcha);
		}
	}

	/**
	 * Stores the registration ID and app versionCode in the application's
	 * {@code SharedPreferences}.
	 *
	 */
	private void storeRegistrationId(String username, String regid)
	{
		final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		int appVersion = getAppVersion();
		Log.i(TAG, "Saving regId on app version " + appVersion);
		SharedPreferences.Editor editor = prefs.edit();
		editor.putString(SettingsActivity.USERNAME, username);
		editor.putString(SettingsActivity.REG_ID, regid);
		editor.putInt(SettingsActivity.PROPERTY_APP_VERSION, appVersion);
		editor.commit();
	}

	/**
	 * Gets the current registration ID for application on GCM service.
	 * <p>
	 * If result is empty, the app needs to register.
	 *
	 * @return registration ID, or empty string if there is no existing
	 *         registration ID.
	 */
	public String getRegistrationId()
	{
		final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		String registrationId = prefs.getString(SettingsActivity.REG_ID, "");
		if (registrationId.isEmpty())
		{
			Log.i(TAG, "Registration not found.");
			return "";
		}

		// Check if app was updated; if so, it must clear the registration ID
		// since the existing regID is not guaranteed to work with the new
		// app version.
		int registeredVersion = prefs.getInt(SettingsActivity.PROPERTY_APP_VERSION, Integer.MIN_VALUE);
		int currentVersion = getAppVersion();
		if (registeredVersion != currentVersion)
		{
			Log.i(TAG, "App version changed.");
			return "";
		}
		return registrationId;
	}

	/**
	 * @return Application's version code from the {@code PackageManager}.
	 */
	public int getAppVersion()
	{
		try
		{
			PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
			return packageInfo.versionCode;
		} catch (PackageManager.NameNotFoundException e)
		{
			// should never happen
			throw new RuntimeException("Could not get package name: " + e);
		}
	}

	/**
	 * Check the device to make sure it has the Google Play Services APK. If it
	 * doesn't, display a dialog that allows users to download the APK from the
	 * Google Play Store or enable it in the device's system settings.
	 */
	public boolean checkPlayServices()
	{
		int resultCode = GooglePlayServicesUtil
			.isGooglePlayServicesAvailable(context);
		if (resultCode != ConnectionResult.SUCCESS)
		{
			if (GooglePlayServicesUtil.isUserRecoverableError(resultCode))
			{
				GooglePlayServicesUtil.getErrorDialog(resultCode, (Activity) context, PLAY_SERVICES_RESOLUTION_REQUEST).show();
			} else
			{
				System.out.println("This device is not supported.");
				((Activity) context).finish();
			}
			return false;
		}
		return true;
	}
}
