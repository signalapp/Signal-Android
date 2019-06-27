package org.thoughtcrime.securesms.webrtc;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.core.app.NotificationCompat;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.WebRtcCallActivity;
import org.thoughtcrime.securesms.notifications.NotificationChannels;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.service.WebRtcCallService;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

/**
 * Manages the state of the WebRtc items in the Android notification bar.
 *
 * @author Moxie Marlinspike
 *
 */

public class CallNotificationBuilder {

  private static final int WEBRTC_NOTIFICATION         = 313388;
  private static final int WEBRTC_NOTIFICATION_RINGING = 313389;

  public static final int TYPE_INCOMING_RINGING    = 1;
  public static final int TYPE_OUTGOING_RINGING    = 2;
  public static final int TYPE_ESTABLISHED         = 3;
  public static final int TYPE_INCOMING_CONNECTING = 4;

  public static Notification getCallInProgressNotification(Context context, int type, Recipient recipient) {
    Intent contentIntent = new Intent(context, WebRtcCallActivity.class);
    contentIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

    PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, contentIntent, 0);

    NotificationCompat.Builder builder = new NotificationCompat.Builder(context, getNotificationChannel(context, type))
                                                               .setSmallIcon(R.drawable.ic_call_secure_white_24dp)
                                                               .setContentIntent(pendingIntent)
                                                               .setOngoing(true)
                                                               .setContentTitle(recipient.getName());

    if (type == TYPE_INCOMING_CONNECTING) {
      builder.setContentText(context.getString(R.string.CallNotificationBuilder_connecting));
      builder.setPriority(NotificationCompat.PRIORITY_MIN);
    } else if (type == TYPE_INCOMING_RINGING) {
      builder.setContentText(context.getString(R.string.NotificationBarManager__incoming_signal_call));
      builder.addAction(getServiceNotificationAction(context, WebRtcCallService.ACTION_DENY_CALL, R.drawable.ic_close_grey600_32dp,   R.string.NotificationBarManager__deny_call));
      builder.addAction(getActivityNotificationAction(context, WebRtcCallActivity.ANSWER_ACTION, R.drawable.ic_phone_grey600_32dp, R.string.NotificationBarManager__answer_call));

      if (callActivityRestricted(context)) {
        builder.setFullScreenIntent(pendingIntent, true);
        builder.setPriority(NotificationCompat.PRIORITY_HIGH);
        builder.setCategory(NotificationCompat.CATEGORY_CALL);
      }
    } else if (type == TYPE_OUTGOING_RINGING) {
      builder.setContentText(context.getString(R.string.NotificationBarManager__establishing_signal_call));
      builder.addAction(getServiceNotificationAction(context, WebRtcCallService.ACTION_LOCAL_HANGUP, R.drawable.ic_call_end_grey600_32dp, R.string.NotificationBarManager__cancel_call));
    } else {
      builder.setContentText(context.getString(R.string.NotificationBarManager_signal_call_in_progress));
      builder.addAction(getServiceNotificationAction(context, WebRtcCallService.ACTION_LOCAL_HANGUP, R.drawable.ic_call_end_grey600_32dp, R.string.NotificationBarManager__end_call));
    }

    return builder.build();
  }

  public static int getNotificationId(@NonNull Context context, int type) {
    if (callActivityRestricted(context) && type == TYPE_INCOMING_RINGING) {
      return WEBRTC_NOTIFICATION_RINGING;
    } else {
      return WEBRTC_NOTIFICATION;
    }
  }

  @SuppressWarnings("BooleanMethodIsAlwaysInverted")
  public static boolean isWebRtcNotification(int notificationId) {
    return notificationId == WEBRTC_NOTIFICATION || notificationId == WEBRTC_NOTIFICATION_RINGING;
  }

  private static @NonNull String getNotificationChannel(@NonNull Context context, int type) {
    if (callActivityRestricted(context) && type == TYPE_INCOMING_RINGING) {
      return NotificationChannels.CALLS;
    } else {
      return NotificationChannels.OTHER;
    }
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

  private static boolean callActivityRestricted(@NonNull Context context) {
    return Build.VERSION.SDK_INT >= 29 && !ApplicationContext.getInstance(context).isAppVisible();
  }
}
