package com.e2eMessenger;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

public class Message
{
	public enum MessageType {KEY_EXCHANGE, MESSAGE, REAUTH, ERROR}
	public enum ErrorType {NOT_REGISTERED, UNAUTHORISED, USER_NOT_EXISTS,
							SEND_LIMIT, INVALID_MESSAGE_TYPE, VALIDATION, GCM, NETWORK, JSON}

	private long messageID;
	private long contactID;
	private boolean sent; // sent or received
	private long timestamp;
	private String message;
	private boolean success;
	
	public Message() {
		
	}

	public Message(long contactID, boolean sent, long timestamp,
			String message, boolean success)
	{
		this.contactID = contactID;
		this.sent = sent;
		this.timestamp = timestamp;
		this.message = message;
		this.success = success;
	}

	public Message(long messageID, long contactID, boolean sent,
			long timestamp, String message, boolean success)
	{
		this.messageID = messageID;
		this.contactID = contactID;
		this.sent = sent;
		this.timestamp = timestamp;
		this.message = message;
		this.success = success;
	}

	// get and setters
	public long getMessageID()
	{
		return messageID;
	}
	public void setMessageID(long messageID)
	{
		this.messageID = messageID;
	}

	public long getContactID()
	{
		return contactID;
	}
	public void setContactID(long contactID)
	{
		this.contactID = contactID;
	}

	public boolean isSent()
	{
		return sent;
	}
	public void setSent(boolean sent)
	{
		this.sent = sent;
	}

	public long getTimestamp()
	{
		return timestamp;
	}
	public void setTimestamp(long timestamp)
	{
		this.timestamp = timestamp;
	}

	public String getMessage()
	{
		return message;
	}
	public void setMessage(String message)
	{
		this.message = message;
	}

	public boolean isSuccess()
	{
		return success;
	}
	public void setSuccess(boolean success)
	{
		this.success = success;
	}

	// Transmit message to server
	public  static void send(final JSONObject data, final MessageType messageType,
							 final Bundle extras, final Context context) // message ID in database
	{
		new AsyncTask<Void, Void, JSONObject>() {

			Exception e;

			@Override
			protected JSONObject doInBackground(Void... params)
			{
				JSONObject resp = null;

				try {
					URL url = new URL(Settings.SERVER_URL + "send");
					HttpURLConnection request = (HttpURLConnection) url.openConnection();
					request.setConnectTimeout(30000);
					request.setDoOutput(true); //POST
					request.setRequestProperty("Content-Type", "application/json");

					// send message to server
					OutputStreamWriter writer = new OutputStreamWriter(request.getOutputStream());
					writer.write(data.toString());
					writer.flush();
					writer.close();
					InputStream is = request.getInputStream();
					Scanner scanner = new Scanner(is);
					String respStr = (scanner.hasNextLine()) ? scanner.nextLine() : null;

					System.out.println(respStr);
					resp = new JSONObject(respStr);

				} catch(IOException e) { // network failure
					Log.e("MESSAGE SEND ERROR", "Message was not successfully sent.");
					e.printStackTrace();
					this.e = e;

				} catch(JSONException e) { // JSON encoding failure
					e.printStackTrace();
					this.e = e;
				}

				return resp;
			}

			@Override
			protected void onPostExecute(JSONObject resp)
			{
				// Error handling
				try
				{
					// if there was a device or sever error
					if(resp == null || resp.getInt("success") == 0)
					{
						ErrorType errorType = null;

						if(resp != null)
						{
							int ordinal = resp.getInt("error_type");
							errorType = ErrorType.values()[ordinal];

							// General errors
							switch(errorType)
							{
								case NOT_REGISTERED:
									notRegistered();
									break;
								case UNAUTHORISED:
									unauthorised();
									break;
								case SEND_LIMIT:
									sendLimitReached();
									break;
								case VALIDATION:
									System.out.println("validation error...");
									//validationError();
									break;
								case INVALID_MESSAGE_TYPE:
									System.out.println("invalid message type...");
									break;
							}
						}
						else if(e instanceof IOException)
						{
							CharSequence text = "Cannot communicate with server.";
							int duration = Toast.LENGTH_LONG;
							Toast toast = Toast.makeText(context, text, duration);
							toast.show();
						}

						switch(messageType)
						{
							case MESSAGE:
								handleMessageFailure();
								break;
							case REAUTH:
								handleReauthorisationFailure();
								break;
							case KEY_EXCHANGE:
								handleKeyExchangeError();
								break;
						}
					}
				} catch(JSONException e) {
					e.printStackTrace();
				}
			}

			/***********************************************************************
			 /* Error handler functions for each message type */

			protected void handleMessageFailure()
			{
				long mid = extras.getLong("messageDBID");

				// Set message success field in DB to false
				DatabaseHelper db = DatabaseHelper.getInstance(context);
				Message message = db.getMessage(mid);
				message.setSuccess(false);
				db.updateMessage(message);

				// Display error message and update UI to show message was not sent
				Intent intent = new Intent("refresh");
				LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
			}

			protected void handleReauthorisationFailure()
			{
				// Show toast
				CharSequence text = "Reauthorisation failed.";
				int duration = Toast.LENGTH_LONG;
				Toast toast = Toast.makeText(context, text, duration);
				toast.show();
			}

			protected void handleKeyExchangeError() throws JSONException
			{
				System.out.println("key exchange failed");
				String username = data.getString("to");

				AsyncTask task = JPAKE.inProgress.get(username);
				if(task != null)
				{
					task.cancel(true);
					JPAKE.inProgress.remove(username);
				}

				DatabaseHelper db = DatabaseHelper.getInstance(context);
				db.invalidateKey(username);

				LocalBroadcastManager broadcastManager = LocalBroadcastManager.getInstance(context);
				broadcastManager.sendBroadcast(new Intent("refresh"));
			}

			/***********************************************************************
			/* Error functions */

			protected void notRegistered()
			{
				// Show toast
				CharSequence text = "Not registered.";
				int duration = Toast.LENGTH_LONG;
				Toast toast = Toast.makeText(context, text, duration);
				toast.show();

				// Set all keys to invalid
				DatabaseHelper db = DatabaseHelper.getInstance(context);
				db.invalidateAllKeys();

				// Display registration dialog
				Activity a = (Activity) context;
				LayoutInflater inflater = a.getLayoutInflater();

				RegistrationHelper regHelper = new RegistrationHelper(context);
				regHelper.showRegistrationDialog(context, true, true);

				/*// Build alert dialog
				AlertDialog dialog = new AlertDialog.Builder(context)
					.setTitle(context.getString(R.string.register_username))
					.setView(inflater.inflate(R.layout.dialog_register, null))
					.setPositiveButton("Ok", new DialogInterface.OnClickListener()
					{
						public void onClick(DialogInterface dialog, int whichButton)
						{
							final String newUsername = input.getText().toString();
							System.out.println("Registering with username: " + newUsername);

							if(newUsername != null) // is valid
							{
								Handler handler = new Handler(context.getMainLooper());
								handler.post(new Runnable()
								{
									@Override
									public void run()
									{
										ProgressDialog progressDialog = ProgressDialog.show(context, "Please wait", "Registering with server...", true);

										RegistrationHelper reg = new RegistrationHelper(context);
										reg.registerInBackground(newUsername, "", "", progressDialog);
									}
								});
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
					.show();*/


				/*final RelativeLayout loading = (RelativeLayout) dialog.findViewById(R.id.loading);
				ReCaptcha reCaptcha = (ReCaptcha) dialog.findViewById(R.id.recaptcha);
				reCaptcha.showChallengeAsync(Settings.RECAPTCHA_PUBLIC_KEY, new ReCaptcha.OnShowChallengeListener()
				{
					@Override
					public void onChallengeShown(boolean shown)
					{
						System.out.println("Challenge shown: " + shown);
						loading.setVisibility(RelativeLayout.INVISIBLE);
					}
				});*/
			}

			protected void unauthorised()
			{
				// Show toast
				CharSequence text = "You are currently unauthorised. Attempting reauthorisation...";
				int duration = Toast.LENGTH_LONG;
				Toast toast = Toast.makeText(context, text, duration);
				toast.show();

				// Set flag to reauthorise
				SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
				SharedPreferences.Editor editor = prefs.edit();
				editor.putBoolean(SettingsActivity.REAUTHORISING, true);
				editor.commit();

				String username = prefs.getString(SettingsActivity.USERNAME, "");
				String regid = prefs.getString(SettingsActivity.REG_ID, "");

				try
				{
					JSONObject req = new JSONObject();
					req.put("username", username);
					req.put("device_id", regid);
					req.put("message_type", MessageType.REAUTH.ordinal());
					req.put("register", false);

					Message.send(req, MessageType.REAUTH, null, context);
				} catch(JSONException e) {
					e.printStackTrace();
				}
			}

			protected void sendLimitReached()
			{
				// Show toast
				CharSequence text = "Send limit reached, please try again later.";
				int duration = Toast.LENGTH_LONG;
				Toast toast = Toast.makeText(context, text, duration);
				toast.show();


			}

			protected void validationError()
			{
				// todo
			}

			protected void gcmError()
			{
				// todo
			}
		}.execute(null, null, null);
	}

/*	// Transmit message to server
	public static void send(final JSONObject data) // message ID in database
	{
		new AsyncTask<Void, Void, JSONObject>() {

			Exception e;

			@Override
			protected JSONObject doInBackground(Void... params)
			{
				JSONObject resp = null;

				try {
					URL url = new URL(Settings.SERVER_URL + "send");
					HttpURLConnection request = (HttpURLConnection) url.openConnection();
					request.setConnectTimeout(30000);
					request.setDoOutput(true); //POST
					request.setRequestProperty("Content-Type", "application/json");

					// send message to server
					OutputStreamWriter writer = new OutputStreamWriter(request.getOutputStream());
					writer.write(data.toString());
					writer.flush();
					writer.close();
					InputStream is = request.getInputStream();
					Scanner scanner = new Scanner(is);
					String respStr = (scanner.hasNextLine()) ? scanner.nextLine() : null;

					System.out.println("response: " + respStr);
					resp = new JSONObject(respStr);

					if(resp.getInt("success") == 1)
					{
						// Success
					}
					else if(resp.getInt("success") == 0)
					{
						// Fail
					}

				} catch(IOException e) {
					Log.e("MESSAGE SEND ERROR", "Message was not successfully sent.");
					e.printStackTrace();
					this.e = e;

				} catch(JSONException e)
				{
					e.printStackTrace();
					this.e = e;
				}
				return resp;
			}

			@Override
			protected void onPostExecute(JSONObject result)
			{

			}
		}.execute(null, null, null);
	}*/
}
