package org.thoughtcrime.securesms.util;

import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Intent;
import android.os.Build;
import android.provider.Telephony;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

public final class SmsUtil {

  private SmsUtil() {
  }

  public static void startActivityToRequestSmsRole(@NonNull Activity activity, int requestCode) {
    if (Build.VERSION.SDK_INT >= 29) {
      RoleManager roleManager = ContextCompat.getSystemService(activity, RoleManager.class);
      Intent      intent      = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS);
      activity.startActivityForResult(intent, requestCode);
    } else {
      Intent intent = new Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT);
      intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, activity.getPackageName());
      activity.startActivity(intent);
    }
  }

  public static void startActivityToRequestSmsRole(@NonNull Fragment fragment, int requestCode) {
    if (Build.VERSION.SDK_INT >= 29) {
      RoleManager roleManager = ContextCompat.getSystemService(fragment.requireContext(), RoleManager.class);
      Intent      intent      = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS);
      fragment.startActivityForResult(intent, requestCode);
    } else {
      Intent intent = new Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT);
      intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, fragment.requireContext().getPackageName());
      fragment.startActivity(intent);
    }
  }
}
