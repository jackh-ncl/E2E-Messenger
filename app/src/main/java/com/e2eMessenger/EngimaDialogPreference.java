package com.e2eMessenger;

import android.content.Context;
import android.preference.DialogPreference;
import android.util.AttributeSet;

/**
 * Class that allows dialogs to be shown easily within the settings activity.
 *
 * <p>For some strange reason you must create your own class that inherits from DialogPreference first.</p>
 *
 * @author Jack Hindmarch
 */
public class EngimaDialogPreference extends DialogPreference
{
	public EngimaDialogPreference(Context context, AttributeSet attrs)
	{
		super(context, attrs);
	}
}
