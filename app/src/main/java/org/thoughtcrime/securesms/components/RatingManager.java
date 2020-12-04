package org.thoughtcrime.securesms.components;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.VersionTracker;

import java.util.concurrent.TimeUnit;

public class RatingManager {

  private static final int DAYS_SINCE_INSTALL_THRESHOLD  = 7;
  private static final int DAYS_UNTIL_REPROMPT_THRESHOLD = 4;

  private static final String TAG = RatingManager.class.getSimpleName();

  public static void showRatingDialogIfNecessary(Context context) {
    if (!TextSecurePreferences.isRatingEnabled(context)) return;

    long daysSinceInstall = VersionTracker.getDaysSinceFirstInstalled(context);
    long laterTimestamp   = TextSecurePreferences.getRatingLaterTimestamp(context);

    if (daysSinceInstall >= DAYS_SINCE_INSTALL_THRESHOLD &&
        System.currentTimeMillis() >= laterTimestamp)
    {
      showRatingDialog(context);
    }
  }

  private static void showRatingDialog(final Context context) {
    new AlertDialog.Builder(context)
        .setTitle(R.string.RatingManager_rate_this_app)
        .setMessage(R.string.RatingManager_if_you_enjoy_using_this_app_please_take_a_moment)
        .setPositiveButton(R.string.RatingManager_rate_now, new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            TextSecurePreferences.setRatingEnabled(context, false);
            startPlayStore(context);
         }
       })
       .setNegativeButton(R.string.RatingManager_no_thanks, new DialogInterface.OnClickListener() {
         @Override
         public void onClick(DialogInterface dialog, int which) {
           TextSecurePreferences.setRatingEnabled(context, false);
         }
       })
       .setNeutralButton(R.string.RatingManager_later, new DialogInterface.OnClickListener() {
         @Override
         public void onClick(DialogInterface dialog, int which) {
           long waitUntil = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(DAYS_UNTIL_REPROMPT_THRESHOLD);
           TextSecurePreferences.setRatingLaterTimestamp(context, waitUntil);
         }
       })
       .show();
  }

  private static void startPlayStore(Context context) {
    Uri uri = Uri.parse("market://details?id=" + context.getPackageName());
    try {
      context.startActivity(new Intent(Intent.ACTION_VIEW, uri));
    } catch (ActivityNotFoundException e) {
      Log.w(TAG, e);
      Toast.makeText(context, R.string.RatingManager_whoops_the_play_store_app_does_not_appear_to_be_installed, Toast.LENGTH_LONG).show();
    }
  }

}
