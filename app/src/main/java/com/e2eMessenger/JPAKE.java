package com.e2eMessenger;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.widget.Toast;

import com.e2eMessenger.Message.MessageType;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import org.bouncycastle.crypto.CryptoException;
import org.bouncycastle.crypto.agreement.jpake.enigma.JPAKEParticipant;
import org.bouncycastle.crypto.agreement.jpake.enigma.JPAKERound1Payload;
import org.bouncycastle.crypto.agreement.jpake.enigma.JPAKERound2Payload;
import org.bouncycastle.crypto.agreement.jpake.enigma.JPAKERound3Payload;
import org.bouncycastle.crypto.digests.SHA256Digest;
import android.util.Base64;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.HashMap;

/**
 * Class which handles each round of the J-PAKE key exchange.
 */
public class JPAKE
{
	private Context context;
	int timeout = 45000;

	public JPAKE(Context context)
	{
		this.context = context;
	}
	public static HashMap<String, AsyncTask> inProgress = new HashMap<String, AsyncTask>();

	public enum Participant {ALICE, BOB}

	/**
	 * If an error occurs, set the contact status to invalid and update database.
	 *
	 * @param from - username of the contact that the error occurred with..
	 * @param context - activity context.
	 */
	public void handleError(final String from, final Context context)
	{
		// key exchange no longer in progress
		// cancel timeout countdown
		AsyncTask task = JPAKE.inProgress.get(from);
		if(task != null)
		{
			task.cancel(true);
			JPAKE.inProgress.remove(from);
		}

		DatabaseHelper db = DatabaseHelper.getInstance(context);
		long uid = db.getContactID(from);
		Contact contact = db.getContact(uid);
		contact.setStatus(Contact.Status.INVALID);
		contact.setSharedKey(null);
		contact.setStatusTime(System.currentTimeMillis());
		db.updateContact(contact);
		db.close();

		// alert data refresh
		broadcastDataChange();

		Handler handler = new Handler(context.getMainLooper());
		handler.post(new Runnable()
		{
			@Override
			public void run()
			{
				// Show toast
				String toastMessage = "Key exchange with " + from + " was unsuccessful.";
				int duration = Toast.LENGTH_LONG;
				Toast toast = Toast.makeText(context, toastMessage, duration);
				toast.show();
			}
		});
	}

	// Timeout a key exchange if it is not completed within 30 seconds.
	private void startCountdown(final String toContact)
	{
		AsyncTask<Void, Void, Boolean> task = new AsyncTask<Void, Void, Boolean>()
		{
			@Override
			protected Boolean doInBackground(Void... params)
			{
				Boolean timedOut = false;
				try
				{
					inProgress.put(toContact, this);
					Thread.sleep(timeout);

					DatabaseHelper db = DatabaseHelper.getInstance(context);
					long uid = db.getContactID(toContact);

					if(uid != -1)
					{
						Contact contact = db.getContact(uid);
						Contact.Status status = contact.getStatus();

						if(status == Contact.Status.PENDING)
						{
							timedOut = true;
							inProgress.remove(toContact);

							// Set contact key status to invalid
							contact.setStatus(Contact.Status.INVALID);
							contact.setStatusTime(System.currentTimeMillis());
							db.updateContact(contact);

							// Broadcast status change
							Intent intent = new Intent("refresh");
							LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
						}
					}
				} catch(InterruptedException e)
				{
					e.printStackTrace();
				}

				return timedOut;
			}

			@Override
			protected void onPostExecute(Boolean timedOut)
			{
				if(timedOut)
				{
					// Show toast
					String toastMessage = "Key exchange with " + toContact + " timed out.";
					int duration = Toast.LENGTH_LONG;
					Toast toast = Toast.makeText(context, toastMessage, duration);
					toast.show();
				}
			}
		};

		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
		{
			task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
		}
		else
		{
			task.execute();
		}
	}

	/****************************************************************************************/
	/* Methods for alice's rounds */
	
	public void aliceRound1(String to, String password)
	{
		try
		{
			// create new participant object
			JPAKEParticipant alice = new JPAKEParticipant("alice", password.toCharArray());
			JPAKERound1Payload aliceRound1Payload = alice.createRound1PayloadToSend();
			
			// encode payload object to string
			String payload = encodeObject(aliceRound1Payload);
			
			// pack message data into json
			String from = getRegisteredUsername();
			JSONObject data = buildJSON(from, to, Participant.ALICE, 1, payload);
			
			// save contact and participant to database
			DatabaseHelper db = DatabaseHelper.getInstance(context);
			long id = db.createKeyExchange(to, alice);
			System.out.println("new key exchange successfully added with id = " + id);
			db.close();
			
			// send data to server
			Message.send(data, MessageType.KEY_EXCHANGE, null, context);

		} catch (JSONException e)
		{
			e.printStackTrace();
		}
	}

	public void aliceRound2(Bundle extras)
	{
		String to = extras.getString("from_user");

		try
		{
			// bob responded, start key exchange timeout countdown
			startCountdown(to);

			// load jpake participant object from db
			DatabaseHelper db = DatabaseHelper.getInstance(context);
			JPAKEParticipant alice = db.getJPAKEParticipant(to);

			// decode received payload and validate
			String bobPayloadStr = extras.getString("payload");
			JPAKERound1Payload bobRound1Payload = (JPAKERound1Payload) decodeString(bobPayloadStr, JPAKERound1Payload.class);
			alice.validateRound1PayloadReceived(bobRound1Payload);

			// create payload 2
			JPAKERound2Payload aliceRound2Payload = alice.createRound2PayloadToSend();

			// save objects to database
			db.updateJPAKEParticipant(to, alice);
			db.close();

			// encode alice payload object to string
			String payload = JPAKE.encodeObject(aliceRound2Payload);

			// pack message data into json
			String from = getRegisteredUsername();
			JSONObject data = buildJSON(from, to, Participant.ALICE, 2, payload);

			// send json to server
			Message.send(data, MessageType.KEY_EXCHANGE, null, context);

		} catch (CryptoException e)
		{
			sendErrorMessage(to);
			e.printStackTrace();
		} catch (IllegalStateException e)
		{
			sendErrorMessage(to);
			e.printStackTrace();
		} catch (JSONException e)
		{
			e.printStackTrace();
		}
	}

	public void aliceSendRound3(Bundle extras)
	{
		String to = extras.getString("from_user");

		try {
			// load jpake participant object from db
			DatabaseHelper db = DatabaseHelper.getInstance(context);
			JPAKEParticipant alice = db.getJPAKEParticipant(to);

			// decode received payload and validate
			String bobPayloadStr = extras.getString("payload");
			JPAKERound2Payload bobRound2Payload = (JPAKERound2Payload) decodeString(bobPayloadStr, JPAKERound2Payload.class);
			alice.validateRound2PayloadReceived(bobRound2Payload);

			// save objects to database
			db.updateJPAKEParticipant(to, alice);

			// calculate keying material and derive session key
			BigInteger aliceKeyingMaterial = alice.calculateKeyingMaterial();
			BigInteger sharedKey = deriveKey(aliceKeyingMaterial);

			// create payload 3 and verify key
			JPAKERound3Payload aliceRound3Payload = alice.createRound3PayloadToSend(aliceKeyingMaterial);

			// save shared key
			long uid = db.getContactID(to);
			Contact contact = db.getContact(uid);
			contact.setSharedKey(sharedKey.toString());
			db.updateContact(contact);

			// encode alice payload object to string
			String payload = JPAKE.encodeObject(aliceRound3Payload);

			// pack message data into json
			String from = getRegisteredUsername();
			JSONObject data = buildJSON(from, to, Participant.ALICE, 3, payload);

			// send json to server
			Message.send(data, MessageType.KEY_EXCHANGE, null, context);

		} catch (CryptoException e)
		{
			sendErrorMessage(to);
			e.printStackTrace();
		} catch (IllegalStateException e)
		{
			sendErrorMessage(to);
			e.printStackTrace();
		} catch (JSONException e)
		{
			e.printStackTrace();
		}
	}

	public void aliceReceiveRound3(Bundle extras)
	{
		final String to = extras.getString("from_user");

		try {
			// load jpake participant object from db
			DatabaseHelper db = DatabaseHelper.getInstance(context);
			JPAKEParticipant alice = db.getJPAKEParticipant(to);

			// calculate keying material and derive session key
			BigInteger aliceKeyingMaterial = alice.calculateKeyingMaterial();

			// decode received payload and validate
			String bobPayloadStr = extras.getString("payload");
			JPAKERound3Payload bobRound3Payload = (JPAKERound3Payload) decodeString(bobPayloadStr, JPAKERound3Payload.class);
			alice.validateRound3PayloadReceived(bobRound3Payload, aliceKeyingMaterial);

			// key exchange no longer in progress
			AsyncTask task = JPAKE.inProgress.get(to);
			if(task != null)
			{
				task.cancel(true);
				JPAKE.inProgress.remove(to);
			}

			// save shared key
			long uid = db.getContactID(to);
			Contact contact = db.getContact(uid);
			contact.setStatus(Contact.Status.VALID);
			contact.setStatusTime(System.currentTimeMillis());
			db.updateContact(contact);

			// remove key exchange data
			db.deleteKeyExchange(uid);
			db.close();

			// alert data refresh
			broadcastDataChange();

			Handler handler = new Handler(context.getMainLooper());
			handler.post(new Runnable()
			{
				@Override
				public void run()
				{
					// Show toast
					String toastMessage = "Key exchange with " + to + " completed successfully.";
					int duration = Toast.LENGTH_LONG;
					Toast toast = Toast.makeText(context, toastMessage, duration);
					toast.show();
				}
			});

		} catch (CryptoException e)
		{
			sendErrorMessage(to);
			e.printStackTrace();
		} catch (IllegalStateException e)
		{
			sendErrorMessage(to);
			e.printStackTrace();
		}
	}

	/****************************************************************************************/
	/* Methods for bob's rounds */
	
	public void bobRound1(String to, String password)
	{
		try {
			JPAKEParticipant bob = new JPAKEParticipant("bob", password.toCharArray());

			// create payload 1
			JPAKERound1Payload bobRound1Payload = bob.createRound1PayloadToSend();

			// decode received payload and validate
			DatabaseHelper db = DatabaseHelper.getInstance(context);
			Contact contact = db.getContact(db.getContactID(to));
			String alicePayloadStr = contact.getRequestPayload();
			JPAKERound1Payload aliceRound1Payload = (JPAKERound1Payload) decodeString(alicePayloadStr, JPAKERound1Payload.class);
			bob.validateRound1PayloadReceived(aliceRound1Payload);

			// save objects to database
			// GET ID FROM USERNAME extras.getString("from_user");
			db.createKeyExchange(to, bob);
			db.close();

			// alert data refresh
			broadcastDataChange();

			// encode bob payload object to string
			String payload = JPAKE.encodeObject(bobRound1Payload);

			// pack message data into json
			String from = getRegisteredUsername();
			JSONObject data = buildJSON(from, to, Participant.BOB, 1, payload);

			// send json to server
			Message.send(data, MessageType.KEY_EXCHANGE, null, context);
			startCountdown(to);

		} catch(CryptoException e) {
			e.printStackTrace();
			sendErrorMessage(to);
		} catch (JSONException e)
		{
			e.printStackTrace();
		}
	}

	public void bobRound2(Bundle extras)
	{
		String to = extras.getString("from_user");

		try {
			// load jpake participant object from db
			DatabaseHelper db = DatabaseHelper.getInstance(context);
			JPAKEParticipant bob = db.getJPAKEParticipant(to);

			// create payload 2
			JPAKERound2Payload bobRound2Payload = bob.createRound2PayloadToSend();

			// decode received payload and validate
			String alicePayloadStr = extras.getString("payload");
			JPAKERound2Payload aliceRound2Payload = (JPAKERound2Payload) decodeString(alicePayloadStr, JPAKERound2Payload.class);
			bob.validateRound2PayloadReceived(aliceRound2Payload);

			// save objects to database
			db.updateJPAKEParticipant(to, bob);
			db.close();

			// encode alice payload object to string
			String payload = JPAKE.encodeObject(bobRound2Payload);

			// pack message data into json
			String from = getRegisteredUsername();
			JSONObject data = buildJSON(from, to, Participant.BOB, 2, payload);

			// send json to server
			Message.send(data, MessageType.KEY_EXCHANGE, null, context);

		} catch (CryptoException e)
		{
			sendErrorMessage(to);
			e.printStackTrace();
		} catch (IllegalStateException e)
		{
			sendErrorMessage(to);
			e.printStackTrace();
		} catch (JSONException e)
		{
			e.printStackTrace();
		}
	}

	public void bobRound3(Bundle extras)
	{
		final String to = extras.getString("from_user");

		try {
			// load jpake participant object from db
			DatabaseHelper db = DatabaseHelper.getInstance(context);
			JPAKEParticipant bob = db.getJPAKEParticipant(to);

			// calculate keying material and derive session key
			BigInteger bobKeyingMaterial = bob.calculateKeyingMaterial();
			BigInteger sharedKey = deriveKey(bobKeyingMaterial);

			// create payload 3
			JPAKERound3Payload bobRound3Payload = bob.createRound3PayloadToSend(bobKeyingMaterial);

			// decode received payload and validate
			String alicePayloadStr = extras.getString("payload");
			JPAKERound3Payload aliceRound3Payload = (JPAKERound3Payload) decodeString(alicePayloadStr, JPAKERound3Payload.class);
			bob.validateRound3PayloadReceived(aliceRound3Payload, bobKeyingMaterial);

			// save objects to database
			db.updateJPAKEParticipant(to, bob); //(for some reason doing this after updating contact causes a database error. no idea why)

			// key exchange no longer in progress
			inProgress.get(to).cancel(true);
			inProgress.remove(to);

			// save shared key
			long uid = db.getContactID(to);
			Contact contact = db.getContact(uid);
			contact.setSharedKey(sharedKey.toString());
			contact.setStatus(Contact.Status.VALID);
			contact.setStatusTime(System.currentTimeMillis());
			db.updateContact(contact);

			// remove key exchange data
			db.deleteKeyExchange(uid);
			db.close();

			Handler handler = new Handler(context.getMainLooper());
			handler.post(new Runnable()
			{
				@Override
				public void run()
				{
					// Show toast
					String toastMessage = "Key exchange with " + to + " completed successfully.";
					int duration = Toast.LENGTH_LONG;
					Toast toast = Toast.makeText(context, toastMessage, duration);
					toast.show();
				}
			});

			// alert data refresh
			broadcastDataChange();

			// encode alice payload object to string
			String payload = JPAKE.encodeObject(bobRound3Payload);

			// pack message data into json
			String from = getRegisteredUsername();
			JSONObject data = buildJSON(from, to, Participant.BOB, 3, payload);

			// send json to server
			Message.send(data, MessageType.KEY_EXCHANGE, null, context);
		}
		catch (CryptoException e)
		{
			sendErrorMessage(to);
			e.printStackTrace();
		} catch (IllegalStateException e)
		{
			sendErrorMessage(to);
			e.printStackTrace();
		} catch (JSONException e)
		{
			e.printStackTrace();
		}
	}


	/**
	 * Packages a JSON encoded message given a number of arguments.
	 *
	 * @param from - user's username
	 * @param to - contact's username (recipient of message)
	 * @param participant - which participant they are in the key exchange (alice or bob)
	 * @param round - which round of the exchange the message is meant for.
	 * @param payload - the base 64 encoded string  of the payload object for the current round.
	 * @return - the packaged JSON encoded string
	 * @throws JSONException
	 */
	private JSONObject buildJSON(String from, String to, Participant participant, int round, String payload) throws JSONException
	{
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		String session_key = prefs.getString(SettingsActivity.SESSION_KEY, "");
		System.out.println(session_key);

		final JSONObject data = new JSONObject();
		data.put("from", from);
		data.put("to", to);
		data.put("session_key", session_key);
		data.put("message_type", MessageType.KEY_EXCHANGE.ordinal());
		data.put("participant", participant.ordinal());
		data.put("round", round);
		data.put("payload", payload);
		
		return data;
	}

	/**
	 * Alert the other contact that an error has occurred and that the key status is now invalid.
	 *
	 * @param to - username of the contact to send the message to.
	 */
	public void sendErrorMessage(final String to)
	{
		try
		{
			// key exchange no longer in progress
			AsyncTask task = JPAKE.inProgress.get(to);
			if(task != null)
			{
				task.cancel(true);
			}
			inProgress.remove(to);

			DatabaseHelper db = DatabaseHelper.getInstance(context);
			long uid = db.getContactID(to);
			Contact contact = db.getContact(uid);
			contact.setStatus(Contact.Status.INVALID);
			contact.setStatusTime(System.currentTimeMillis());
			db.updateContact(contact);
			db.close();

			// alert data refresh
			broadcastDataChange();

			Handler handler = new Handler(context.getMainLooper());
			handler.post(new Runnable()
			{
				@Override
				public void run()
				{
					// Show toast
					String toastMessage = "Key exchange with " + to + " was unsuccessful.";
					int duration = Toast.LENGTH_LONG;
					Toast toast = Toast.makeText(context, toastMessage, duration);
					toast.show();
				}
			});

			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
			String session_key = prefs.getString(SettingsActivity.SESSION_KEY, "");
			String from = getRegisteredUsername();

			final JSONObject data = new JSONObject();
			data.put("from", from);
			data.put("to", to);
			data.put("session_key", session_key);
			data.put("message_type", MessageType.ERROR.ordinal());
			//data.put("error_type", "key_exchange");

			Message.send(data, MessageType.ERROR, null, context);
		}
		catch (JSONException e)
		{
			e.printStackTrace();
		}
	}

	private void broadcastDataChange()
	{
		Intent intent = new Intent("refresh");
		LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
	}

	/**
	 * @param keyingMaterial - keying material from round 2 of J-PAKE.
	 * @return - SHA-256 HASH of the keying material.
	 */
    private BigInteger deriveKey(BigInteger keyingMaterial)
    {
        SHA256Digest digest = new SHA256Digest();
        byte[] keyByteArray = keyingMaterial.toByteArray();
        byte[] output = new byte[digest.getDigestSize()];
        
        digest.update(keyByteArray, 0, keyByteArray.length);
        digest.doFinal(output, 0);

        return new BigInteger(output);
    }

	/**
	 * @return - registered username stored on device.
	 */
    private String getRegisteredUsername()
    {
    	if(context == null) System.out.println("context is null");
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context); // might need to change arg1
		String from = prefs.getString(SettingsActivity.USERNAME, "");
    	return from;
    }

	/**
	 * Takes an object and serialises it to a byte array, and then encodes it into a base 64 string.
	 *
	 * @param o - object to serialise.
	 * @return - base 64 string representation of serialised object.
	 */
	public static String encodeObject(Object o)
	{
		Kryo kryo = new Kryo();
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		Output output = new Output(baos);
		kryo.writeObject(output, o);
		output.close();
		byte[] bytes = baos.toByteArray();
		//String encStr = Base64.toBase64String(bytes);
		String encStr = Base64.encodeToString(bytes, Base64.DEFAULT);

		return encStr;
	}

	/**
	 * Takes a base 64 string, decodes it, and attempts to deserialise into an object of the specified
	 * class type.
	 *
	 * @param encStr - base 64 string representation of serialised object.
	 * @param classType - type of class to deserialise to.
	 * @return
	 */
	public static Object decodeString(String encStr, Class<?> classType)
	{	
		Kryo kryo = new Kryo();
		byte[] bytes = Base64.decode(encStr, Base64.DEFAULT);
		ByteArrayInputStream baos = new ByteArrayInputStream(bytes);
		Input input = new Input(baos);
		Object obj = kryo.readObject(input, classType);
		input.close();
		
		return obj;
	}

}
