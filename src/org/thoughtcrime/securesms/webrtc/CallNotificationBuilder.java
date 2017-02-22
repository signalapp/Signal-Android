package org.thoughtcrime.securesms.webrtc;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.support.v4.app.NotificationCompat;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.WebRtcCallActivity;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.service.WebRtcCallService;
import org.thoughtcrime.securesms.util.ServiceUtil;

/**
 * Manages the state of the WebRtc items in the Android notification bar.
 *
 * @author Moxie Marlinspike
 *
 */

public class CallNotificationBuilder {

  public static final int WEBRTC_NOTIFICATION   = 313388;

  public static final int TYPE_INCOMING_RINGING = 1;
  public static final int TYPE_OUTGOING_RINGING = 2;
  public static final int TYPE_ESTABLISHED      = 3;

  public static Notification getCallInProgressNotification(Context context, int type, Recipient recipient) {
    Intent contentIntent = new Intent(context, WebRtcCallActivity.class);
    contentIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

    PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, contentIntent, 0);

    NotificationCompat.Builder builder = new NotificationCompat.Builder(context)
        .setSmallIcon(R.drawable.ic_call_secure_white_24dp)
        .setContentIntent(pendingIntent)
        .setOngoing(true)
        .setContentTitle(recipient.getName());

    if (type == TYPE_INCOMING_RINGING) {
      builder.setContentText(context.getString(R.string.NotificationBarManager__incoming_signal_call));
      builder.addAction(getServiceNotificationAction(context, WebRtcCallService.ACTION_DENY_CALL, R.drawable.ic_close_grey600_32dp,   R.string.NotificationBarManager__deny_call));
      builder.addAction(getActivityNotificationAction(context, WebRtcCallActivity.ANSWER_ACTION, R.drawable.ic_phone_grey600_32dp, R.string.NotificationBarManager__answer_call));
    } else if (type == TYPE_OUTGOING_RINGING) {
      builder.setContentText(context.getString(R.string.NotificationBarManager__establishing_signal_call));
      builder.addAction(getServiceNotificationAction(context, WebRtcCallService.ACTION_LOCAL_HANGUP, R.drawable.ic_call_end_grey600_32dp, R.string.NotificationBarManager__cancel_call));
    } else {
      builder.setContentText(context.getString(R.string.NotificationBarManager_signal_call_in_progress));
      builder.addAction(getServiceNotificationAction(context, WebRtcCallService.ACTION_LOCAL_HANGUP, R.drawable.ic_call_end_grey600_32dp, R.string.NotificationBarManager__end_call));
    }

    return builder.build();
  }

  private static NotificationCompat.Action getServiceNotificationAction(Context context, String action, int iconResId, int titleResId) {
    Intent intent = new Intent(context, WebRtcCallService.class);
    intent.setAction(action);

    PendingIntent pendingIntent = PendingIntent.getService(context, 0, intent, 0);

    return new NotificationCompat.Action(iconResId, context.getString(titleResId), pendingIntent);
  }

  private static NotificationCompat.Action getActivityNotificationAction(@NonNull Context context, @NonNull String action,
                                                                         @DrawableRes int iconResId, @StringRes int titleResId)
  {
    Intent intent = new Intent(context, WebRtcCallActivity.class);
    intent.setAction(action);

    PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);

    return new NotificationCompat.Action(iconResId, context.getString(titleResId), pendingIntent);
  }
}
