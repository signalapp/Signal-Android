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

import org.thoughtcrime.securesms.MainActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.WebRtcCallActivity;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.notifications.NotificationChannels;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.service.webrtc.WebRtcCallService;

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
                                                               .setContentTitle(recipient.getDisplayName(context));

    if (type == TYPE_INCOMING_CONNECTING) {
      builder.setContentText(context.getString(R.string.CallNotificationBuilder_connecting));
      builder.setPriority(NotificationCompat.PRIORITY_MIN);
    } else if (type == TYPE_INCOMING_RINGING) {
      builder.setContentText(context.getString(recipient.isGroup() ? R.string.NotificationBarManager__incoming_signal_group_call : R.string.NotificationBarManager__incoming_signal_call));
      builder.addAction(getServiceNotificationAction(context, WebRtcCallService.denyCallIntent(context), R.drawable.ic_close_grey600_32dp, R.string.NotificationBarManager__decline_call));
      builder.addAction(getActivityNotificationAction(context, WebRtcCallActivity.ANSWER_ACTION, R.drawable.ic_phone_grey600_32dp, recipient.isGroup() ? R.string.NotificationBarManager__join_call : R.string.NotificationBarManager__answer_call));

      if (callActivityRestricted()) {
        builder.setFullScreenIntent(pendingIntent, true);
        builder.setPriority(NotificationCompat.PRIORITY_HIGH);
        builder.setCategory(NotificationCompat.CATEGORY_CALL);
      }
    } else if (type == TYPE_OUTGOING_RINGING) {
      builder.setContentText(context.getString(R.string.NotificationBarManager__establishing_signal_call));
      builder.addAction(getServiceNotificationAction(context, WebRtcCallService.hangupIntent(context), R.drawable.ic_call_end_grey600_32dp, R.string.NotificationBarManager__cancel_call));
    } else {
      builder.setContentText(context.getString(R.string.NotificationBarManager_signal_call_in_progress));
      builder.addAction(getServiceNotificationAction(context, WebRtcCallService.hangupIntent(context), R.drawable.ic_call_end_grey600_32dp, R.string.NotificationBarManager__end_call));
    }

    return builder.build();
  }

  public static int getNotificationId(int type) {
    if (callActivityRestricted() && type == TYPE_INCOMING_RINGING) {
      return WEBRTC_NOTIFICATION_RINGING;
    } else {
      return WEBRTC_NOTIFICATION;
    }
  }

  public static @NonNull Notification getStoppingNotification(@NonNull Context context) {
    Intent contentIntent = new Intent(context, MainActivity.class);
    contentIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

    PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, contentIntent, 0);

    return new NotificationCompat.Builder(context, NotificationChannels.CALL_STATUS).setSmallIcon(R.drawable.ic_call_secure_white_24dp)
                                                                                    .setContentIntent(pendingIntent)
                                                                                    .setOngoing(true)
                                                                                    .setContentTitle(context.getString(R.string.NotificationBarManager__stopping_signal_call_service))
                                                                                    .setPriority(NotificationCompat.PRIORITY_MIN)
                                                                                    .build();
  }

  public static int getStoppingNotificationId() {
    return WEBRTC_NOTIFICATION;
  }

  @SuppressWarnings("BooleanMethodIsAlwaysInverted")
  public static boolean isWebRtcNotification(int notificationId) {
    return notificationId == WEBRTC_NOTIFICATION || notificationId == WEBRTC_NOTIFICATION_RINGING;
  }

  private static @NonNull String getNotificationChannel(@NonNull Context context, int type) {
    if (callActivityRestricted() && type == TYPE_INCOMING_RINGING) {
      return NotificationChannels.CALLS;
    } else {
      return NotificationChannels.CALL_STATUS;
    }
  }

  private static NotificationCompat.Action getServiceNotificationAction(Context context, Intent intent, int iconResId, int titleResId) {
    PendingIntent pendingIntent = Build.VERSION.SDK_INT >= 26 ? PendingIntent.getForegroundService(context, 0, intent, 0)
                                                              : PendingIntent.getService(context, 0, intent, 0);

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

  private static boolean callActivityRestricted() {
    return Build.VERSION.SDK_INT >= 29 && !ApplicationDependencies.getAppForegroundObserver().isForegrounded();
  }
}
