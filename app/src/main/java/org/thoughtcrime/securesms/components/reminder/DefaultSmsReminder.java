package org.thoughtcrime.securesms.components.reminder;

import android.content.Context;
import android.view.View;
import android.view.View.OnClickListener;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.SmsUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;

public class DefaultSmsReminder extends Reminder {

  public DefaultSmsReminder(@NonNull Fragment fragment, short requestCode) {
    super(fragment.getString(R.string.reminder_header_sms_default_title),
          fragment.getString(R.string.reminder_header_sms_default_text));

    final OnClickListener okListener = new OnClickListener() {
      @Override
      public void onClick(View v) {
        TextSecurePreferences.setPromptedDefaultSmsProvider(fragment.requireContext(), true);
        fragment.startActivityForResult(SmsUtil.getSmsRoleIntent(fragment.requireContext()), requestCode);
      }
    };
    final OnClickListener dismissListener = new OnClickListener() {
      @Override
      public void onClick(View v) {
        TextSecurePreferences.setPromptedDefaultSmsProvider(fragment.requireContext(), true);
      }
    };
    setOkListener(okListener);
    setDismissListener(dismissListener);
  }

  public static boolean isEligible(Context context) {
    final boolean isDefault = Util.isDefaultSmsProvider(context);
    if (isDefault) {
      TextSecurePreferences.setPromptedDefaultSmsProvider(context, false);
    }

    return !isDefault && !TextSecurePreferences.hasPromptedDefaultSmsProvider(context);
  }
}
