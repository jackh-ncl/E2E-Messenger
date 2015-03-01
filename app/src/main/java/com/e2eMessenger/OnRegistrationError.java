package com.e2eMessenger;

/**
 * Created by jack on 09/11/14.
 */
public interface OnRegistrationError
{
	public void handleError(RegistrationHelper.ErrorType errorType);
}
