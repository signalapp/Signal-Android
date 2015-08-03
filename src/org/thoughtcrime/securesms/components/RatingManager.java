package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Log;

import com.afollestad.materialdialogs.MaterialDialog;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import java.util.concurrent.TimeUnit;

public class RatingManager {

  private static final int DAYS_SINCE_INSTALL_THRESHOLD  = 7;
  private static final int DAYS_UNTIL_REPROMPT_THRESHOLD = 4;

  private static final String TAG = RatingManager.class.getSimpleName();

  public static void showRatingDialogIfNecessary(Context context) {
    if (!TextSecurePreferences.isRatingEnabled(context)) return;

    long daysSinceInstall = getDaysSinceInstalled(context);
    long laterTimestamp   = TextSecurePreferences.getRatingLaterTimestamp(context);

    if (daysSinceInstall >= DAYS_SINCE_INSTALL_THRESHOLD &&
        System.currentTimeMillis() >= laterTimestamp)
    {
      showRatingDialog(context);
    }
  }

  private static void showRatingDialog(final Context context) {
    new MaterialDialog.Builder(context)
        .title(context.getString(R.string.RatingManager_rate_this_app))
        .content(context.getString(R.string.RatingManager_if_you_enjoy_using_this_app_please_take_a_moment))
        .positiveText(context.getString(R.string.RatingManager_rate_now))
        .negativeText(context.getString(R.string.RatingManager_no_thanks))
        .neutralText(context.getString(R.string.RatingManager_later))
        .callback(new MaterialDialog.ButtonCallback() {
          @Override
          public void onPositive(MaterialDialog dialog) {
            TextSecurePreferences.setRatingEnabled(context, false);
            startPlayStore(context);
            super.onPositive(dialog);
          }

          @Override
          public void onNegative(MaterialDialog dialog) {
            TextSecurePreferences.setRatingEnabled(context, false);
            super.onNegative(dialog);
          }

          @Override
          public void onNeutral(MaterialDialog dialog) {
            long waitUntil = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(DAYS_UNTIL_REPROMPT_THRESHOLD);
            TextSecurePreferences.setRatingLaterTimestamp(context, waitUntil);
            super.onNeutral(dialog);
          }
        })
        .show();
  }

  private static void startPlayStore(Context context) {
    Uri uri = Uri.parse("market://details?id=" + context.getPackageName());
    Intent intent = new Intent(Intent.ACTION_VIEW, uri);
    if (intent.resolveActivity(context.getPackageManager()) != null) {
      context.startActivity(intent);
    }

  }

  private static long getDaysSinceInstalled(Context context) {
    try {
      long installTimestamp = context.getPackageManager()
                                     .getPackageInfo(context.getPackageName(), 0)
                                     .firstInstallTime;

      return TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - installTimestamp);
    } catch (PackageManager.NameNotFoundException e) {
      Log.w(TAG, e);
      return 0;
    }
  }

}
