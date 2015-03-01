package com.e2eMessenger;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Base64;
import android.widget.Toast;

import com.e2eMessenger.Contact.Status;
import com.e2eMessenger.JPAKE.Participant;
import com.e2eMessenger.Message.MessageType;
import com.google.android.gms.gcm.GoogleCloudMessaging;

import java.util.HashSet;

/**
 * Service that runs in the background, and while the phone is asleep.
 *
 * <p>Handles new messages received from the GCM servers, and then decides what to do with
 * them.</p>
 *
 * @author Jack Hindmarch
 */
public class GcmIntentService extends IntentService
{
	public static final String NEW_CONTACT_REQUEST = "com.enigma.NEW_CONTACT_REQUEST";
	private static final String TAG = "GcmNotify";
	public static final int NOTIFICATION_ID = 1;

	public GcmIntentService() 
	{
		super("GcmIntentService");
	}

	/**
	 * Receive new message from GCM, and determine the GCM message type.
	 * @param intent - intent with the attached message bundle.
	 */
	@Override
	protected void onHandleIntent(Intent intent)
	{
		Bundle extras = intent.getExtras();
		GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(this);
		// The getMessageType() intent parameter must be the intent you received in your BroadcastReceiver.
		String messageType = gcm.getMessageType(intent);

		if (!extras.isEmpty())
		{
			if (GoogleCloudMessaging.MESSAGE_TYPE_SEND_ERROR.equals(messageType))
			{
				// Do nothing - need to implement
			}
			else if (GoogleCloudMessaging.MESSAGE_TYPE_DELETED.equals(messageType))
			{
				// Do nothing - need to implement
			} // If it's a regular GCM message, do some work.
			else if (GoogleCloudMessaging.MESSAGE_TYPE_MESSAGE.equals(messageType))
			{
				handle(extras);
			}
		}
		// Release the wake lock provided by the WakefulBroadcastReceiver.
		GcmBroadcastReceiver.completeWakefulIntent(intent);
	}

	/**
	 * Takes the message payload, determines the message type and then
	 * executes necessary methods.
	 *
	 * @param extras - message bundle.
	 */
	private void handle(Bundle extras)
	{
		int ordinal = Integer.parseInt(extras.getString("enigma_message_type"));
		Message.MessageType messageType = Message.MessageType.values()[ordinal];

		// Fetch shared preferences
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		boolean receiveRequests = prefs.getBoolean(SettingsActivity.RECEIVE_REQUESTS, true);

		if(messageType == Message.MessageType.MESSAGE)
		{
			handleMessage(extras);
		}
		else if(messageType == Message.MessageType.KEY_EXCHANGE && receiveRequests)
		{
			String username = extras.getString("from_user");

			int ord = Integer.valueOf( extras.getString("participant") );
			Participant participant = Participant.values()[ord];

			if(participant == Participant.ALICE && extras.getString("round").equals("1") && !userBlocked(username))
			{
				AsyncTask task = JPAKE.inProgress.get(username);
				if(task != null)
				{
					task.cancel(true);
					JPAKE.inProgress.remove(username);
				}

				// Save new contact
				long timestamp = System.currentTimeMillis();

				Contact contact = new Contact(
					username,
					Contact.Status.NEW_REQUEST,
					timestamp,
					timestamp
				);
				contact.setRequestPayload(extras.getString("payload"));

				DatabaseHelper db = DatabaseHelper.getInstance(this);
				db.insertOrUpdateContact(contact);
				db.close();

				Intent intent2 = new Intent("refresh");
				LocalBroadcastManager.getInstance(this).sendBroadcast(intent2);

				// Make new notification
				String msg = String.format("New contact request from %s.", username);
				Intent intent = new Intent(this, MainActivity.class);
				//intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
				intent.putExtra(NEW_CONTACT_REQUEST, true);
				PendingIntent contentIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
				sendNotification(msg, contentIntent, MessageType.KEY_EXCHANGE, username);
			}
			else if(userExists(username) && !userBlocked(username) && getUserStatus(username) == Status.PENDING)
			{
				JPAKE jpake = new JPAKE(this);

				int round = Integer.parseInt( extras.getString("round") );

				if(participant == JPAKE.Participant.ALICE)
				{
					// actions for bob
					switch(round)
					{
						case 2:
							System.out.println("Payload 2 received from alice");
							jpake.bobRound2(extras);
							break;
						case 3:
							System.out.println("Payload 3 received from alice");
							jpake.bobRound3(extras);
							break;
						default:
							break;
					}
				}
				else if(participant == Participant.BOB)
				{
					// actions for alice
					switch(round)
					{
						case 1:
							System.out.println("Payload 1 received from bob");
							jpake.aliceRound2(extras);
							break;
						case 2:
							System.out.println("Payload 2 received from bob");
							jpake.aliceSendRound3(extras);
							break;
						case 3:
							System.out.println("Payload 3 received from bob");
							jpake.aliceReceiveRound3(extras);
							break;
						default:
							break;
					}
				}

			}
			else if(userExists(username) && !userBlocked(username) && getUserStatus(username) == Status.INVALID)
			{
				JPAKE jpake = new JPAKE(this);
				jpake.sendErrorMessage(username);
			}
		}
		else if(messageType == Message.MessageType.ERROR)
		{
			String username = extras.getString("from_user");

			if(/*extras.getString("error_type").equals("key_exchange") &&*/ userExists(username) && !userBlocked(username))
			{
				JPAKE jpake = new JPAKE(this);
				jpake.handleError(extras.getString("from_user"), getApplicationContext());
			}
		}
		else if(messageType == Message.MessageType.REAUTH)
		{
			System.out.println("Received reauthorisation message. Is it legit?");
			boolean reauthorising = prefs.getBoolean(SettingsActivity.REAUTHORISING, false);
			System.out.println("reauthorising: " + reauthorising);

			if(reauthorising)
			{
				System.out.println("Reauthorisation is legit");
				SharedPreferences.Editor editor = prefs.edit();
				String session_key = extras.getString("session_key");
				editor.putString(SettingsActivity.SESSION_KEY, session_key);
				editor.putBoolean(SettingsActivity.REAUTHORISING, false);
				editor.commit();

				final boolean register = extras.getBoolean("register");
				Handler handler = new Handler(this.getMainLooper());

				handler.post(new Runnable()
				{
					@Override
					public void run()
					{
						// Make a new toast
						CharSequence text = (register) ?
							"Reauthorisation successful, please submit your details again." :
							"Reauthorisation successful.";
						int duration = Toast.LENGTH_LONG;
						Toast toast = Toast.makeText(GcmIntentService.this, text, duration);
						toast.show();
					}
				});

				// If reauthorisation was due to registration error, then reauthorise
				/*boolean reg = Boolean.parseBoolean( extras.getString("register") );
				if(reg)
				{
					String username = extras.getString("username");
					//ProgressDialog progressDialog = ProgressDialog.show(this, "Please wait", "Registering with server...", true); // causes error

					RegistrationHelper regHelper = new RegistrationHelper(this);
					regHelper.registerInBackground(username, "", "", "", null, null);
				}*/
			}
		}
	}

	/**
	 * Returns whether or not a user exists with the supplied username,
	 * and whether or not they are blocked.
	 *
	 * @param username - username of the message sender.
	 * @return - true or false.
	 */
	private boolean userExists(String username)
	{
		// Fetch contact ID
		DatabaseHelper db = DatabaseHelper.getInstance(this);
		long contactID = db.getContactID(username);
		Contact contact = db.getContact(contactID);

		return contact != null;
	}

	private boolean userBlocked(String username)
	{
		DatabaseHelper db = DatabaseHelper.getInstance(this);
		HashSet<String> blockedUsers = db.getBlockedUserHashSet();

		if(blockedUsers.contains(username)) System.out.println(username + ": blocked.");

		return blockedUsers.contains(username);
	}

	private Status getUserStatus(String username)
	{
		// Fetch contact
		DatabaseHelper db = DatabaseHelper.getInstance(this);
		long contactID = db.getContactID(username);
		Contact contact = db.getContact(contactID);

		return contact.getStatus();
	}

	/**
	 * Handles a GCM message of type private message.
	 *
	 * <p>If it comes from a valid user: decrypt, store and notify.</p>
	 *
	 * @param extras - the message bundle.
	 */
	private void handleMessage(Bundle extras)
	{
		// Fetch contact ID
		String username = extras.getString("from_user");
		DatabaseHelper db = DatabaseHelper.getInstance(this);
		long contactID = db.getContactID(username);
		Contact contact = db.getContact(contactID);

		// Only if valid contact exists in database and receive request setting is true
		if(userExists(username) && !userBlocked(username) && contact.getStatus() == Status.VALID)
		{
			// Decrypt message before storing
			String keyString = db.getSharedKey(contactID);

			AES aes = new AES(keyString);
			byte[] enc = Base64.decode(extras.getString("message"), Base64.DEFAULT);
			byte[] IV = Base64.decode(extras.getString("IV"), Base64.DEFAULT);

			byte[] dec = null;

			try {
				dec = aes.decrypt(enc, IV);

				if(contact.getStatus() == Contact.Status.PENDING)
				{
					contact.setStatus(Contact.Status.VALID);
					db.updateContact(contact);
				}
			} catch(Exception e)
			{
				e.printStackTrace();

				JPAKE jpake = new JPAKE(this);
				jpake.sendErrorMessage(username);
				return;
			}

			String msg = new String(dec);

			// Store message in database
			Message message = new Message();
			message.setContactID(contactID);
			message.setMessage(msg);
			message.setTimestamp(System.currentTimeMillis());
			message.setSuccess(true);
			message.setSent(false);
			db.createMessage(message);

			// Post notification of received message.
			String notifyMsg = String.format("%s: %s", contact.getNick(), msg);

			// Send contact id to conversations activity to know which messages to load
			Intent intent = new Intent(this, ConversationActivity.class);
			intent.putExtra("uid", contactID);
			intent.putExtra("notification", true);
			intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
			PendingIntent contentIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

			sendNotification(notifyMsg, contentIntent, MessageType.MESSAGE, contact.getUsername());
			db.close();

			// Alert in case of conversations activity being open
			Intent intent2 = new Intent("refresh");
			LocalBroadcastManager.getInstance(this).sendBroadcast(intent2);
		}
		else if(userExists(username) && !userBlocked(username) && contact.getStatus() == Contact.Status.INVALID)
		{
			JPAKE jpake = new JPAKE(this);
			jpake.sendErrorMessage(extras.getString("from_user"));
		}
	}

	/**
	 * Builds and executes a new notification.
	 *
	 * <p>Uses the users settings to customise the notification (or display it at all)</p>
	 *
	 * @param msg
	 * @param contentIntent
	 */
	private void sendNotification(String msg, PendingIntent contentIntent, MessageType messageType, String fromUser)
	{
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		boolean notificationsOn = prefs.getBoolean(SettingsActivity.NOTIFICATIONS, true);
		boolean vibrateOn = prefs.getBoolean(SettingsActivity.NOTIFICATIONS_VIBRATE, true);
		String ringtone = prefs.getString(SettingsActivity.NOTIFICATIONS_RINGTONE, "content://settings/system/notification_sound");

		if(notificationsOn && (messageType == MessageType.KEY_EXCHANGE || ConversationActivity.currentConversationPartner == null
				||!ConversationActivity.currentConversationPartner.getUsername().equals(fromUser)))
		{
			NotificationManager mNotificationManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);

			int defaults = Notification.DEFAULT_LIGHTS;
			NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this)
				.setSmallIcon(R.drawable.ic_launcher)
				.setContentTitle(getString(R.string.app_name))
				.setStyle(new NotificationCompat.BigTextStyle().bigText(msg))
				.setPriority(NotificationCompat.PRIORITY_HIGH)
				.setSound(Uri.parse(ringtone))
				.setContentText(msg);

			mBuilder.setContentIntent(contentIntent);
			if(vibrateOn)
			{
				defaults |= Notification.DEFAULT_VIBRATE;
			}

			mBuilder.setDefaults(defaults);

			Notification notification = mBuilder.build();
			notification.flags = Notification.FLAG_AUTO_CANCEL | Notification.FLAG_SHOW_LIGHTS;

			mNotificationManager.notify(NOTIFICATION_ID, notification);
		}
	}
}