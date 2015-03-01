package com.e2eMessenger;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.RelativeLayout;

import com.e2eMessenger.recaptcha.ReCaptcha;

/**
 * Created by jack on 05/11/14.
 */
public class RegistrationDialog extends AlertDialog.Builder
{
	private Context context;
	RelativeLayout loading;
	ReCaptcha reCaptcha;
	EditText reCaptchaEntry;
	EditText passwordEntry;

	public RegistrationDialog(Context context)
	{
		super(context);
		this.context = context;
	}

	public void showMe(final boolean requirePassword, final boolean requireCaptcha)
	{
		Activity a = (Activity) context;
		LayoutInflater inflater = a.getLayoutInflater();
		final View layout = inflater.inflate(R.layout.dialog_register, null);

		setTitle("Register username");
		setView(layout);

		if(requirePassword)
		{
			passwordEntry = (EditText) layout.findViewById(R.id.password);
			passwordEntry.setVisibility(EditText.VISIBLE);
		}

		if(requireCaptcha)
		{
			reCaptcha = (ReCaptcha) layout.findViewById(R.id.recaptcha);
			loading = (RelativeLayout) layout.findViewById(R.id.loading);
			reCaptchaEntry = (EditText) layout.findViewById(R.id.recaptcha_input);

			loading.setVisibility(RelativeLayout.VISIBLE);
			reCaptcha.setVisibility(ReCaptcha.VISIBLE);
			reCaptchaEntry.setVisibility(EditText.VISIBLE);
			reCaptcha.showChallengeAsync(Settings.RECAPTCHA_PUBLIC_KEY, new ReCaptcha.OnShowChallengeListener()
			{
				@Override
				public void onChallengeShown(boolean shown)
				{
					loading.setVisibility(RelativeLayout.GONE);
				}
			});
		}

		setPositiveButton("Ok", new DialogInterface.OnClickListener()
		{
			public void onClick(DialogInterface dialog, int whichButton)
			{
				String username = "";
				String reCaptchaResp = "";
				String challenge = "";
				String password = "";

				EditText usernameEntry = (EditText) layout.findViewById(R.id.username_entry);
				username = usernameEntry.getText().toString();

				if(requireCaptcha)
				{
					reCaptchaResp = reCaptchaEntry.getText().toString();
					challenge = reCaptcha.getLastChallenge(Settings.RECAPTCHA_PUBLIC_KEY);
				}

				if(requirePassword)
				{
					password = passwordEntry.getText().toString();
				}

				System.out.println("Registering with username: " + username);

				if(Contact.isValidUsername(username))
				{
					ProgressDialog progressDialog = ProgressDialog.show(context, "Please wait", "Registering with server...", true);

					RegistrationHelper reg = new RegistrationHelper(context);
					reg.registerInBackground(username, reCaptchaResp, challenge, password, progressDialog, null);
				} else
				{
					showMe(requirePassword, requireCaptcha);
				}
			}
		});
		setNegativeButton("Cancel", new DialogInterface.OnClickListener()
		{
			public void onClick(DialogInterface dialog, int whichButton)
			{
				// Do nothing.
			}
		});

		show();
	}


}
