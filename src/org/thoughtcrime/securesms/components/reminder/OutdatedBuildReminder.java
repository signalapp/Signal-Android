package org.thoughtcrime.securesms.components.reminder;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.Util;

public class OutdatedBuildReminder extends Reminder {

  private static final String TAG = OutdatedBuildReminder.class.getSimpleName();

  public OutdatedBuildReminder(final Context context) {
    super(context.getString(R.string.reminder_header_outdated_build),
          getPluralsText(context));
    setOkListener(v -> {
      try {
        context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + context.getPackageName())));
      } catch (ActivityNotFoundException anfe) {
        try {
          context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + context.getPackageName())));
        } catch (ActivityNotFoundException anfe2) {
          Log.w(TAG, anfe2);
          Toast.makeText(context, R.string.OutdatedBuildReminder_no_web_browser_installed, Toast.LENGTH_LONG).show();
        }
      }
    });
  }

  private static CharSequence getPluralsText(final Context context) {
    int days = Util.getDaysTillBuildExpiry() - 1;
    if (days == 0) {
      return context.getString(R.string.reminder_header_outdated_build_details_today);
    }
    return context.getResources().getQuantityString(R.plurals.reminder_header_outdated_build_details, days, days);
  }

  @Override
  public boolean isDismissable() {
    return false;
  }

  public static boolean isEligible() {
    return Util.getDaysTillBuildExpiry() <= 10;
  }

}
