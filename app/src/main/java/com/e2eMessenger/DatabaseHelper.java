package com.e2eMessenger;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.bouncycastle.crypto.agreement.jpake.enigma.JPAKEParticipant;

import java.util.HashSet;

/**
 * Database helper class that provides useful methods for
 * interfacing with a SQLite database.
 *
 * @author Jack Hindmarch
 */
public class DatabaseHelper extends SQLiteOpenHelper 
{
	private static DatabaseHelper mInstance = null;

	// Logcat tag
	private static final String LOG = "DatabaseHelper";

	// Database Version
	private static final int DATABASE_VERSION = 1;

	// Database Name
	public static final String DATABASE_NAME = "enigma";

	// Table Names
	public static final String TABLE_CONTACT = "Contact";
	public static final String TABLE_MESSAGE = "Message";
	public static final String TABLE_KEY_EXCHANGE = "Key_Exchange";
	public static final String TABLE_BLOCKED_USERS = "Blocked_Users";

	// Contact Table - fields
	public static final String KEY_UID = "_id";
	public static final String KEY_USERNAME = "username";
	public static final String KEY_NICK = "nick";
	public static final String KEY_SHARED_KEY = "shared_key";
	public static final String KEY_STATUS = "status"; // (friend/pending/key exchange error)
	public static final String KEY_STATUS_TIME = "status_time";
	public static final String KEY_LAST_READ = "last_read";
	public static final String KEY_REQUEST_PAYLOAD = "request_payload";
	
	// Message - fields
	public static final String KEY_MID = "_id";
	public static final String KEY_SENT = "sent"; // sent or received (1/0)
	public static final String KEY_TIMESTAMP = "timestamp";
	public static final String KEY_MESSAGE = "message";
	public static final String KEY_SUCCESS = "success"; // delivery success
	
	// Key_Exchange - fields
	public static final String KEY_JPAKE_PARTICIPANT = "jpake_participant";

	// Block_Users - fields
	public static final String KEY_BID = "_id";
	//public static final String KEY_USERNAME = "username";

	/** 
	 * Table Create Statements 
	 * **/

	// Contact table create statement
	private static final String CREATE_TABLE_CONTACT = "CREATE TABLE "
			+ TABLE_CONTACT + "(" + KEY_UID + " INTEGER PRIMARY KEY," + KEY_USERNAME + " TEXT UNIQUE NOT NULL,"
			+ KEY_NICK + " TEXT NOT NULL," + KEY_SHARED_KEY + " TEXT," + KEY_STATUS  + " INTEGER NOT NULL,"
			+ KEY_STATUS_TIME + " INTEGER NOT NULL," + KEY_LAST_READ + " INTEGER NOT NULL," + KEY_REQUEST_PAYLOAD + " TEXT)";

	// Message table create statement
	private static final String CREATE_TABLE_MESSAGE = "CREATE TABLE " + TABLE_MESSAGE 
			+ "(" + KEY_MID + " INTEGER PRIMARY KEY," + "uid INTEGER NOT NULL," + KEY_SENT
			+ " INTEGER NOT NULL," + KEY_TIMESTAMP + " INTEGER NOT NULL," + KEY_MESSAGE + " TEXT NOT NULL," 
			+ KEY_SUCCESS + " INTEGER," + "FOREIGN KEY(uid) REFERENCES "
			+ TABLE_CONTACT + "(" + KEY_UID + ") ON DELETE CASCADE" + ")";

	// Key Exchange table create statement
	private static final String CREATE_TABLE_KEY_EXCHANGE = "CREATE TABLE "
			+ TABLE_KEY_EXCHANGE + "(" + KEY_UID + " INTEGER PRIMARY KEY," 
			+ KEY_JPAKE_PARTICIPANT + " TEXT NOT NULL," + " FOREIGN KEY(" + KEY_UID + ") REFERENCES " 
			+ TABLE_CONTACT + "(" + KEY_UID + ") ON DELETE CASCADE" + ")";

	// Blocked table create statement
	private static final String CREATE_TABLE_BLOCKED_USERS = "CREATE TABLE "
		+ TABLE_BLOCKED_USERS + "(" + KEY_BID + " INTEGER PRIMARY KEY," + KEY_USERNAME + " TEXT UNIQUE NOT NULL)";

	public static DatabaseHelper getInstance(Context context)
	{
		// Use the application context, which will ensure that you
		// don't accidentally leak an Activity's context.
		if (mInstance == null)
		{
			mInstance = new DatabaseHelper(context.getApplicationContext());
		}
		return mInstance;
	}

	public DatabaseHelper(Context context)
	{
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
	}

	/**
	 * Creates each of the tables if they do not already exist.
	 */
	@Override
	public void onCreate(SQLiteDatabase db) 
	{
		db.execSQL(CREATE_TABLE_CONTACT);
		db.execSQL(CREATE_TABLE_MESSAGE);
		db.execSQL(CREATE_TABLE_KEY_EXCHANGE);
		db.execSQL(CREATE_TABLE_BLOCKED_USERS);
	}

	/**
	 * If database has been upgraded, delete the current tables and recreate.
	 */
	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) 
	{
		// on upgrade drop older tables
		db.execSQL("DROP TABLE IF EXISTS " + TABLE_CONTACT);
		db.execSQL("DROP TABLE IF EXISTS " + TABLE_MESSAGE);
		db.execSQL("DROP TABLE IF EXISTS " + TABLE_KEY_EXCHANGE);
		db.execSQL("DROP TABLE IF EXISTS " + TABLE_BLOCKED_USERS);

		// create new tables
		onCreate(db);
	}

	@Override
	public void onOpen(SQLiteDatabase db)
	{
		super.onOpen(db);

		// Enables foreign key relations
		db.execSQL("PRAGMA foreign_keys=ON");
	}

	public boolean isOpen()
	{
		SQLiteDatabase db = this.getWritableDatabase();
		return db.isOpen();
	}

	/****************************************************************/

	/* Contact table operations	 */

	/**
	 * @param contact - contact to create.
	 * @return - id value of the newly inserted record. -1 if failed.
	 */
	public long createContact(Contact contact)
	{
		SQLiteDatabase db = this.getWritableDatabase();

		ContentValues values = new ContentValues();
		values.put(KEY_USERNAME, contact.getUsername());
		values.put(KEY_STATUS, contact.getStatus().ordinal());
		values.put(KEY_STATUS_TIME, contact.getStatusTime());
		values.put(KEY_LAST_READ, contact.getLastReadTime());
		values.put(KEY_REQUEST_PAYLOAD, contact.getRequestPayload());

		if(contact.getNick() == null)
		{
			values.put(KEY_NICK, contact.getUsername());
		}
		else
		{
			values.put(KEY_NICK, contact.getNick());
		}

		// insert row
		return db.insert(TABLE_CONTACT, null, values);
	}

	/**
	 * Takes a contact and checks if it currently exists in the database.
	 *
	 * <p>If it does then update it, if not then insert it.</p>
	 *
	 * @param contact - contact to insert or update.
	 * @return - id value of the contact if insert, number of rows affected if update (-1 if fails).
	 */
	public long insertOrUpdateContact(Contact contact)
	{
		SQLiteDatabase db = this.getWritableDatabase();
		long uid = getContactID(contact.getUsername());

		if(uid == -1)
		{
			return createContact(contact);
		}
		else
		{
			Contact c = getContact(uid);
			contact.setNick(c.getNick());
			contact.setSharedKey(null);
			return updateContact(contact);
		}
	}

	/**
	 * Fetches the contact of a specified id number, and returns a Java object representation of
	 * the record.
	 *
	 * @param id - id of the contact.
	 * @return - contact that is fetched (null if does not exist).
	 */
	public Contact getContact(long id) // add error handling in event of no result
	{
		SQLiteDatabase db = this.getReadableDatabase();
		
		String query = "SELECT * FROM " + TABLE_CONTACT + " WHERE " + KEY_UID + " = ?";
		Cursor c = db.rawQuery(query, new String[] { String.valueOf(id) });
		
		Contact contact = null;
		if(c.moveToFirst()) 
		{
			contact = new Contact(
				c.getString(c.getColumnIndex(KEY_USERNAME)),
				c.getString(c.getColumnIndex(KEY_NICK)),
				c.getString(c.getColumnIndex(KEY_SHARED_KEY)),
				Contact.Status.values()[c.getInt(c.getColumnIndex(KEY_STATUS))],
				c.getLong(c.getColumnIndex(KEY_STATUS_TIME)),
				c.getLong(c.getColumnIndex(KEY_LAST_READ)),
				c.getString(c.getColumnIndex(KEY_REQUEST_PAYLOAD))
			);
		}
		c.close();
		
		return contact;
	}

	/**
	 * Fetches a cursor containing every contact in the database.
	 *
	 * <p>New contact requests appear at the beginning of the list.</p>
	 *
	 * @return
	 */
	public Cursor[] getAllContacts()
	{
		SQLiteDatabase db = this.getReadableDatabase();

		String q1 = "SELECT * FROM " + TABLE_CONTACT + " WHERE " + KEY_STATUS + " = ? ORDER BY LOWER(" + KEY_NICK + "), " + KEY_USERNAME;
		Cursor c1 = db.rawQuery(q1, new String[] {String.valueOf(Contact.Status.NEW_REQUEST.ordinal())} );

		String q2 = "SELECT * FROM " + TABLE_CONTACT + " WHERE " + KEY_STATUS + " != ? ORDER BY LOWER(" + KEY_NICK + "), " + KEY_USERNAME;
		Cursor c2 = db.rawQuery(q2, new String[] {String.valueOf(Contact.Status.NEW_REQUEST.ordinal())} );

		return new Cursor[] {c1, c2};
	}

	public int getContactCount()
	{
		SQLiteDatabase db = this.getReadableDatabase();

		String query = "SELECT COUNT(*) FROM " + TABLE_CONTACT;
		Cursor c = db.rawQuery(query, null);
		c.moveToFirst();

		return c.getInt(0);
	}

	/*public Cursor getAuthenticatedContacts()
	{
		SQLiteDatabase db = this.getReadableDatabase();
		String query = "SELECT * FROM " + TABLE_CONTACT + " WHERE " + KEY_STATUS + " = ?";

		return db.rawQuery(query, new String[] {String.valueOf(Contact.Status.VALID.ordinal())});
	}*/

	/**
	 * Returns the shared key stored for the given contact.
	 *
	 * @param uid - id of the contact.
	 * @return - string representation of the shared key.
	 */
	public String getSharedKey(long uid)
	{
		SQLiteDatabase db = this.getReadableDatabase();

		String query = "SELECT " + KEY_SHARED_KEY + " FROM " + TABLE_CONTACT + " WHERE " + KEY_UID + " = ?";
		Cursor c = db.rawQuery(query, new String[] { String.valueOf(uid) });

		String keyString = null;
		if(c.moveToFirst())
		{
			keyString = c.getString(0);
		}
		c.close();

		return keyString;
	}

	/**
	 * Return a contact id given their username.
	 *
	 * @param username - username of the contact.
	 * @return - id of the contact is they exist, else -1.
	 */
	public long getContactID(String username)
	{
		SQLiteDatabase db = this.getReadableDatabase();
		String query = "SELECT _id FROM " + TABLE_CONTACT + " WHERE " + KEY_USERNAME + " = ?";
		
		Cursor c = db.rawQuery(query, new String[] { username });
		if(c.moveToFirst())
		{
			long uid = c.getLong(0);
			c.close();
			return uid;
		}
		else return -1;
	}

	/**
	 * Update a contact with the current values stored in the supplied contact object.
	 *
	 * @param contact - contact to update.
	 * @return - the number of rows affected.
	 */
	public int updateContact(Contact contact)
	{
		SQLiteDatabase db = this.getWritableDatabase();

		long uid = getContactID(contact.getUsername());

		ContentValues values = new ContentValues();
		values.put(KEY_USERNAME, contact.getUsername());
		values.put(KEY_NICK, contact.getNick());
		values.put(KEY_SHARED_KEY, contact.getSharedKey());
		values.put(KEY_STATUS, contact.getStatus().ordinal());
		values.put(KEY_STATUS_TIME, contact.getStatusTime());
		values.put(KEY_LAST_READ, contact.getLastReadTime());
		values.put(KEY_REQUEST_PAYLOAD, contact.getRequestPayload());

		// updating row
		return db.update(TABLE_CONTACT, values, KEY_UID + " = ?", new String[] { String.valueOf(uid) });
	}

	/**
	 * Delete a specified contact.
	 *
	 * @param uid - id of the contact to be deleted.
	 */
	public void deleteContact(long uid)
	{
		SQLiteDatabase db = this.getWritableDatabase();
		db.delete(TABLE_CONTACT, KEY_UID + " = ?", new String[]{String.valueOf(uid)});
	}

	public int invalidateKey(String username)
	{
		SQLiteDatabase db = this.getWritableDatabase();

		ContentValues values = new ContentValues();
		values.put(KEY_STATUS, Contact.Status.INVALID.ordinal());
		values.put(KEY_STATUS_TIME, System.currentTimeMillis());

		return db.update(TABLE_CONTACT, values, KEY_USERNAME + " = ?", new String[] {username});
	}

	public int invalidateAllKeys()
	{
		SQLiteDatabase db = this.getWritableDatabase();

		ContentValues values = new ContentValues();
		values.put(KEY_STATUS, Contact.Status.INVALID.ordinal());
		values.put(KEY_STATUS_TIME, System.currentTimeMillis());

		return db.update(TABLE_CONTACT, values, null, null);
	}

	/****************************************************************/

	/* Message table operations */

	/**
	 * @param message - message to create.
	 * @return - id value of the newly inserted record. -1 if failed.
	 */	public long createMessage(Message message)
	{
		SQLiteDatabase db = this.getWritableDatabase();

		ContentValues values = new ContentValues();
		values.put(KEY_SENT, message.isSent());
		values.put(KEY_TIMESTAMP, message.getTimestamp());
		values.put(KEY_MESSAGE, message.getMessage());
		values.put("uid", message.getContactID());
		values.put(KEY_SUCCESS, message.isSuccess());

		// insert row
		return db.insert(TABLE_MESSAGE, null, values);
	}

	/**
	 * Fetches the message of a specified id number, and returns a Java object representation of
	 * the record.
	 *
	 * @param id - id of the message.
	 * @return - message that is fetched (null if does not exist).
	 */
	public Message getMessage(long id)
	{
		SQLiteDatabase db = this.getReadableDatabase();

		String query = "SELECT * FROM " + TABLE_MESSAGE + " WHERE " + KEY_MID + " = ?";
		Cursor c = db.rawQuery(query, new String[] { String.valueOf(id) });

		Message message = null;
		if(c.moveToFirst())
		{
			message = new Message(
				c.getLong(c.getColumnIndex(KEY_MID)),
				c.getLong(c.getColumnIndex("uid")),
				c.getInt(c.getColumnIndex(KEY_SENT)) == 1,
				c.getLong(c.getColumnIndex(KEY_TIMESTAMP)),
				c.getString(c.getColumnIndex(KEY_MESSAGE)),
				c.getInt(c.getColumnIndex(KEY_SUCCESS)) == 1
			);
		}
		c.close();

		return message;
	}

	/**
	 * Returns all stored messages with a specified user (sent and received).
	 *
	 * @param contactID - id of the contact to query.
	 * @return - cursor filled with matching records.
	 */
	public Cursor getAllMessages(long contactID)
	{
		SQLiteDatabase db = this.getReadableDatabase();
		String query = "SELECT * FROM " + TABLE_MESSAGE + " WHERE uid = ?";
		
		Cursor c = db.rawQuery(query, new String[] {String.valueOf(contactID)});
		System.out.println("Message count for user = " + c.getCount());
				
		return c;
	}

	public int getMessageCountForContact(long uid)
	{
		SQLiteDatabase db = this.getReadableDatabase();

		String query = "SELECT COUNT(*) FROM " + TABLE_MESSAGE + " WHERE uid = ?";
		Cursor c = db.rawQuery(query, new String[] {String.valueOf(uid)});
		c.moveToFirst();

		return c.getInt(0);
	}

	/**
	 * Returns the conversation list used in ConversationsFragment.
	 *
	 * <p>Selects the most recent message (grouped by contact), and is sorted by most
	 * recent to oldest.</p>
	 *
	 * @return
	 */
	public Cursor getMostRecentMessages()
	{
		SQLiteDatabase db = this.getReadableDatabase();
		String query = "SELECT uid, " + KEY_LAST_READ + ", " + TABLE_MESSAGE + "." + KEY_MID + ", " + KEY_MESSAGE + ", MAX(" + KEY_TIMESTAMP + ") AS maxTimestamp FROM "
			+ TABLE_MESSAGE + ", " + TABLE_CONTACT + " WHERE uid=" + TABLE_CONTACT + "." + KEY_UID + " GROUP BY uid ORDER BY "
			+ KEY_TIMESTAMP + " DESC";

		Cursor c = db.rawQuery(query, new String[] {});
		return c;
	}

	/**
	 * Fetch the id of the contact that the message belongs to.
	 *
	 * @param mid - id of the message.
	 * @return - id of the contact.
	 */
	public long getUIDFromMID(long mid)
	{
		SQLiteDatabase db = this.getReadableDatabase();
		String query = "SELECT uid FROM " + TABLE_MESSAGE + " WHERE " + KEY_MID + " = ?";

		Cursor c = db.rawQuery(query, new String[] { String.valueOf(mid) });
		c.moveToFirst();
		long uid = c.getLong(0);
		return uid;
	}

	/**
	 * Deletes all messages belonging to a specified contact.
	 *
	 * @param uid - id of the contact whose messages should be deleted.
	 */
	public void deleteMessages(long uid)
	{
		SQLiteDatabase db = this.getWritableDatabase();
		db.delete(TABLE_MESSAGE, "uid = ?", new String[]{String.valueOf(uid)});
	}

	public int updateMessage(Message message)
	{
		SQLiteDatabase db = this.getWritableDatabase();

		//long uid = getContactID(contact.getUsername());

		ContentValues values = new ContentValues();
		values.put(KEY_SENT, message.isSent());
		values.put(KEY_TIMESTAMP, message.getTimestamp());
		values.put(KEY_MESSAGE, message.getMessage());
		values.put("uid", message.getContactID());
		values.put(KEY_SUCCESS, message.isSuccess());

		// updating row
		return db.update(TABLE_MESSAGE, values, KEY_MID + " = ?", new String[] { String.valueOf(message.getMessageID()) });
	}

	/**
	 * Delete a single message.
	 *
	 * @param messageID - id of the message.
	 */
	public void deleteMessage(long messageID) 
	{
		SQLiteDatabase db = this.getWritableDatabase();
		db.delete(TABLE_MESSAGE, KEY_MID + " = ?", new String[]{String.valueOf(messageID)});
	}
	
	/****************************************************************/

	/* Key Exchange table operations */

	/**
	 * Returns whether or not a key exchange currently exists with a given contact.
	 *
	 * @param uid - id of the contact.
	 * @return - true or false.
	 */
	public boolean keyExchangeExists(long uid)
	{
		SQLiteDatabase db = this.getReadableDatabase();
		String query = "SELECT * FROM " + TABLE_KEY_EXCHANGE + " WHERE " + KEY_UID + " = ?";

		Cursor c = db.rawQuery(query, new String[] {String.valueOf(uid)});
		return c.moveToFirst();
	}

	/**
	 * Creates a new active key exchange with a contact.
	 *
	 * <p>If contact doesn't already exist, then insert them into database.</p>
	 *
	 * @param username - username of the contact.
	 * @param jp - participant object storing the state and calculation values of the current exchange.
	 * @return - row id of newly inserted record.
	 */
	public long createKeyExchange(String username, JPAKEParticipant jp)
	{
		SQLiteDatabase db = this.getWritableDatabase();

		// check if contact already exists
		long uid = getContactID(username);
		Contact contact = null;
		if(uid == -1)
		{
			long timestamp = System.currentTimeMillis();

			contact = new Contact(
				username,
				Contact.Status.PENDING,
				timestamp,
				timestamp
			);
			uid = createContact(contact);
		}
		else
		{
			contact = getContact(uid);
			contact.setStatus(Contact.Status.PENDING);
			contact.setStatusTime(System.currentTimeMillis());
			contact.setSharedKey(null);
			updateContact(contact);
		}

		String jpStr = JPAKE.encodeObject(jp);

		ContentValues values = new ContentValues();
		values.put(KEY_UID, uid);
		values.put(KEY_JPAKE_PARTICIPANT, jpStr);

		// insert row
		if(keyExchangeExists(uid))
		{
			updateJPAKEParticipant(contact.getUsername(), jp);
			return uid;
		}
		else
		{
			return db.insert(TABLE_KEY_EXCHANGE, null, values);
		}
	}

	/**
	 * Returns participant object of a given contact.
	 *
	 * @param username - username of the contact.
	 * @return - participant object, or null if it doesn't exist.
	 */
	public JPAKEParticipant getJPAKEParticipant(String username)
	{
		SQLiteDatabase db = this.getReadableDatabase();
		
		String query = "SELECT * FROM " + TABLE_KEY_EXCHANGE + ", " + TABLE_CONTACT  + " WHERE " 
				+ TABLE_KEY_EXCHANGE + "." + KEY_UID + " = " + TABLE_CONTACT + "." + KEY_UID 
				+ " AND " + TABLE_CONTACT + "." + KEY_USERNAME + " = ?";
		
		Cursor c = db.rawQuery(query, new String[] { username });
		
		String jpStr = null;
		if(c.moveToFirst()) 
		{
			jpStr = c.getString(c.getColumnIndex(KEY_JPAKE_PARTICIPANT));
		}
		c.close();
		return (JPAKEParticipant) JPAKE.decodeString(jpStr, JPAKEParticipant.class);
	}

	/**
	 * Update the key exchange data with the latest participant object.
	 *
	 * @param username - username of the contact.
	 * @param jp - participant object.
	 * @return - number of updated rows.
	 */
	public int updateJPAKEParticipant(String username, JPAKEParticipant jp)
	{
		SQLiteDatabase db = this.getWritableDatabase();
		long id = getContactID(username);
		String jpStr = JPAKE.encodeObject(jp);
		
		ContentValues values = new ContentValues();
		values.put(KEY_JPAKE_PARTICIPANT, jpStr);

		// updating row
		return db.update(TABLE_KEY_EXCHANGE, values, KEY_UID + " = ?", new String[] { String.valueOf(id) });
	}

	public void deleteKeyExchange(long uid)
	{
		SQLiteDatabase db = this.getWritableDatabase();
		db.delete(TABLE_KEY_EXCHANGE, KEY_UID + " = ?", new String[]{ String.valueOf(uid) });
	}

	/****************************************************************/

	/* Blocked users operations */

	public long insertBlockedUser(String username)
	{
		SQLiteDatabase db = this.getWritableDatabase();

		ContentValues values = new ContentValues();
		values.put(KEY_USERNAME, username);

		return db.insert(TABLE_BLOCKED_USERS, null, values);
	}

	public boolean isUserBlocked(String username)
	{
		SQLiteDatabase db = this.getReadableDatabase();
		String query = "SELECT * FROM " + TABLE_BLOCKED_USERS + " WHERE " + KEY_USERNAME + " = ?";

		Cursor c = db.rawQuery(query, new String[] {username});
		return c.moveToFirst();
	}

	public String getBlockedUser(long id)
	{
		SQLiteDatabase db = this.getReadableDatabase();
		String query = "SELECT * FROM " + TABLE_BLOCKED_USERS + " WHERE " + KEY_BID + " = ?";

		Cursor c = db.rawQuery(query, new String[] {String.valueOf(id)});

		String username = null;
		if(c.moveToFirst())
		{
			username = 	c.getString(c.getColumnIndex(KEY_USERNAME));
		}

		return username;
	}

	public Cursor getBlockedUsers()
	{
		SQLiteDatabase db = this.getReadableDatabase();
		String query = "SELECT * FROM " + TABLE_BLOCKED_USERS + " ORDER BY " + KEY_USERNAME;

		Cursor c = db.rawQuery(query, new String[] {});
		return c;
	}

	public HashSet<String> getBlockedUserHashSet()
	{
		HashSet<String> hashSet = new HashSet<String>();
		Cursor c = getBlockedUsers();

		while(c.moveToNext())
		{
			String username = c.getString(c.getColumnIndex(KEY_USERNAME));
			hashSet.add(username);
		}

		return hashSet;
	}

	public void removeBlockedUser(long id)
	{
		SQLiteDatabase db = this.getWritableDatabase();
		db.delete(TABLE_BLOCKED_USERS, KEY_BID + " = ?", new String[]{String.valueOf(id)});
	}

	public void removeBlockedUser(String username)
	{
		SQLiteDatabase db = this.getWritableDatabase();
		db.delete(TABLE_BLOCKED_USERS, KEY_USERNAME + " = ?", new String[]{username});
	}
}