package org.thoughtcrime.securesms.components;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.provider.Telephony;
import android.view.View;
import android.view.View.OnClickListener;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;

public class DefaultSmsReminder extends Reminder {

  @TargetApi(VERSION_CODES.KITKAT)
  public DefaultSmsReminder(final Context context) {
    super(R.drawable.sms_selection_icon,
          R.string.reminder_header_sms_default_title,
          R.string.reminder_header_sms_default_text);

    final OnClickListener okListener = new OnClickListener() {
      @Override
      public void onClick(View v) {
        TextSecurePreferences.setPromptedDefaultSmsProvider(context, true);
        Intent intent = new Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT);
        intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.getPackageName());
        context.startActivity(intent);
      }
    };
    final OnClickListener cancelListener = new OnClickListener() {
      @Override
      public void onClick(View v) {
        TextSecurePreferences.setPromptedDefaultSmsProvider(context, true);
      }
    };
    setOkListener(okListener);
    setCancelListener(cancelListener);
  }

  public static boolean isEligible(Context context) {
    final boolean isDefault = Util.isDefaultSmsProvider(context);
    if (isDefault) {
      TextSecurePreferences.setPromptedDefaultSmsProvider(context, false);
    }

    return !isDefault && !TextSecurePreferences.hasPromptedDefaultSmsProvider(context);
  }
}
