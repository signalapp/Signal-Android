package org.thoughtcrime.securesms.components.reminder;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.Util;

public class ExpiredBuildReminder extends Reminder {
  @SuppressWarnings("unused")
  private static final String TAG = ExpiredBuildReminder.class.getSimpleName();

  public ExpiredBuildReminder(final Context context) {
    super(context.getString(R.string.reminder_header_expired_build),
          context.getString(R.string.reminder_header_expired_build_details));
    setOkListener(v -> {
      try {
        context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + context.getPackageName())));
      } catch (android.content.ActivityNotFoundException anfe) {
        try {
          context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + context.getPackageName())));
        } catch (android.content.ActivityNotFoundException anfe2) {
          Log.w(TAG, anfe2);
          Toast.makeText(context, R.string.OutdatedBuildReminder_no_web_browser_installed, Toast.LENGTH_SHORT).show();
        }
      }
    });
  }

  @Override
  public boolean isDismissable() {
    return false;
  }

  public static boolean isEligible() {
    return Util.getDaysTillBuildExpiry() <= 0;
  }

}
