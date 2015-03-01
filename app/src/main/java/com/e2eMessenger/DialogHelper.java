package com.e2eMessenger;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface.OnClickListener;

/**
 * @author Jack Hindmarch
 * @since 1.01
 *
 * Convenience class for showing simple alert dialogs
 * that only require a title and body.
 */
public class DialogHelper
{
	public static void showSimpleAlert(Context context, String title, String body)
	{
		new AlertDialog.Builder(context)
			.setTitle(title)
			.setMessage(body)
			.setPositiveButton("Close", null)
			.show();
	}
}
