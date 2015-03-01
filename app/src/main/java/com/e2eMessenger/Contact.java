package com.e2eMessenger;

import java.util.regex.Pattern;

/**
 * This is the class representation of a
 * contact stored in the client database.
 *
 * @author Jack Hindmarch
 */
public class Contact
{
	private String username;
	private String nick;
	private String sharedKey;
	private Status status;
	private long statusTime;
	private long lastReadTime;
	private String requestPayload;

	public enum Status {PENDING, VALID, INVALID, NEW_REQUEST}

	public Contact() {

	}

	public Contact(String username, Status status, long statusTime, long lastReadTime)
	{
		this.username = username;
		this.status = status;
		this.statusTime = statusTime;
		this.lastReadTime = lastReadTime;
	}


	public Contact(String username, String nick, String sharedKey, Status status, long statusTime, long lastReadTime, String requestPayload)
	{
		this.username = username;
		this.nick = nick;
		this.sharedKey = sharedKey;
		this.status = status;
		this.statusTime = statusTime;
		this.lastReadTime = lastReadTime;
		this.requestPayload = requestPayload;
	}

	// validation methods
	public static boolean isValidUsername(String username)
	{
		Pattern p = Pattern.compile("[a-z0-9_-]{3,15}");
		return p.matcher(username).matches();
	}

	public static boolean isValidNick(String nick)
	{
		Pattern p = Pattern.compile("[a-z0-9_-]{3,15}");
		return p.matcher(nick).matches();
	}

	// get and setters
	public String getUsername()
	{
		return username;
	}
	public void setUsername(String username)
	{
		this.username = username;
	}

	public String getSharedKey(){ return sharedKey; }
	public void setSharedKey(String sharedKey)
	{
		this.sharedKey = sharedKey;
	}

	public Status getStatus()
	{
		return status;
	}
	public void setStatus(Status status)
	{
		this.status = status;
	}

	public long getStatusTime() { return statusTime; }
	public void setStatusTime(long statusTime) { this.statusTime = statusTime; }

	public String getNick()	{ return nick; }
	public void setNick(String nick) { this.nick = nick; }

	public long getLastReadTime() { return lastReadTime; }
	public void setLastReadTime(long lastReadTime){	this.lastReadTime = lastReadTime; }

	public String getRequestPayload() { return requestPayload; }
	public void setRequestPayload(String requestPayload) { this.requestPayload = requestPayload; }
}
