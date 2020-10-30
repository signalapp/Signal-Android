package org.thoughtcrime.securesms.util;

import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Telephony;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

public final class SmsUtil {

  private SmsUtil() {
  }

  /**
   * Must be used with {@code startActivityForResult}
   */
  public static @NonNull Intent getSmsRoleIntent(@NonNull Context context) {
    if (Build.VERSION.SDK_INT >= 29) {
      RoleManager roleManager = ContextCompat.getSystemService(context, RoleManager.class);
      return roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS);
    } else {
      Intent intent = new Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT);
      intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.getPackageName());
      return intent;
    }
  }
}
