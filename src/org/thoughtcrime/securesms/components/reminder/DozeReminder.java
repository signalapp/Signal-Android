package org.thoughtcrime.securesms.components.reminder;


import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.view.View;

import org.thoughtcrime.securesms.util.TextSecurePreferences;

public class DozeReminder extends Reminder {

  @RequiresApi(api = Build.VERSION_CODES.M)
  public DozeReminder(@NonNull final Context context) {
    super("Optimize for missing Play Services",
          "This device does not support Play Services. Tap to disable system battery optimizations that prevent Signal from retrieving messages while inactive.");

    setOkListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        TextSecurePreferences.setPromptedOptimizeDoze(context, true);
        Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                   Uri.parse("package:" + context.getPackageName()));
        context.startActivity(intent);
      }
    });

    setDismissListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        TextSecurePreferences.setPromptedOptimizeDoze(context, true);
      }
    });
  }

  public static boolean isEligible(Context context) {
    return TextSecurePreferences.isGcmDisabled(context)            &&
           !TextSecurePreferences.hasPromptedOptimizeDoze(context) &&
           Build.VERSION.SDK_INT >= Build.VERSION_CODES.M          &&
           !((PowerManager)context.getSystemService(Context.POWER_SERVICE)).isIgnoringBatteryOptimizations(context.getPackageName());
  }

}
