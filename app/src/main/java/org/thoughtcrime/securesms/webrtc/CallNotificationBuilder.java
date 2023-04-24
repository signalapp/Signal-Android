package org.thoughtcrime.securesms.webrtc;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import org.signal.core.util.PendingIntentFlags;
import org.thoughtcrime.securesms.MainActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.WebRtcCallActivity;
import org.thoughtcrime.securesms.notifications.NotificationChannels;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.service.webrtc.WebRtcCallService;
import org.thoughtcrime.securesms.util.ConversationUtil;
import org.thoughtcrime.securesms.util.ServiceUtil;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Manages the state of the WebRtc items in the Android notification bar.
 *
 * @author Moxie Marlinspike
 */

public class CallNotificationBuilder {

  private static final int WEBRTC_NOTIFICATION         = 313388;
  private static final int WEBRTC_NOTIFICATION_RINGING = 313389;

  public static final int TYPE_INCOMING_RINGING    = 1;
  public static final int TYPE_OUTGOING_RINGING    = 2;
  public static final int TYPE_ESTABLISHED         = 3;
  public static final int TYPE_INCOMING_CONNECTING = 4;

  /**
   * This is the API level at which call style notifications will
   * properly pop over the screen and allow a user to answer a call.
   * <p>
   * Older API levels will still render a notification with the proper
   * actions, but since we want to ensure that they are able to answer
   * the call without having to open the shade, we fall back on launching
   * the activity (done so in SignalCallManager).
   */
  public static final int API_LEVEL_CALL_STYLE = 29;

  public static Single<Notification> getCallInProgressNotification(Context context, int type, Recipient recipient) {
    Intent contentIntent = new Intent(context, WebRtcCallActivity.class);
    contentIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
    contentIntent.putExtra(WebRtcCallActivity.EXTRA_STARTED_FROM_FULLSCREEN, true);

    PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, contentIntent, PendingIntentFlags.mutable());

    NotificationCompat.Builder builder = new NotificationCompat.Builder(context, getNotificationChannel(type))
        .setSmallIcon(R.drawable.ic_call_secure_white_24dp)
        .setContentIntent(pendingIntent)
        .setOngoing(true)
        .setContentTitle(recipient.getDisplayName(context));

    if (type == TYPE_INCOMING_CONNECTING) {
      builder.setContentText(context.getString(R.string.CallNotificationBuilder_connecting));
      builder.setPriority(NotificationCompat.PRIORITY_MIN);
      builder.setContentIntent(null);
      return Single.just(builder.build());
    } else if (type == TYPE_INCOMING_RINGING) {
      builder.setContentText(context.getString(recipient.isGroup() ? R.string.NotificationBarManager__incoming_signal_group_call : R.string.NotificationBarManager__incoming_signal_call));
      builder.setPriority(NotificationCompat.PRIORITY_HIGH);
      builder.setCategory(NotificationCompat.CATEGORY_CALL);

      if (deviceVersionSupportsIncomingCallStyle() &&
          ServiceUtil.getPowerManager(context).isInteractive() &&
          !ServiceUtil.getKeyguardManager(context).isDeviceLocked())
      {
        return Single.fromCallable(() -> ConversationUtil.buildPerson(context, recipient))
                     .subscribeOn(Schedulers.io())
                     .observeOn(AndroidSchedulers.mainThread())
                     .map(person -> {
                       builder.setStyle(NotificationCompat.CallStyle.forIncomingCall(
                           person,
                           getServicePendingIntent(context, WebRtcCallService.denyCallIntent(context)),
                           getActivityPendingIntent(context, WebRtcCallActivity.ANSWER_ACTION)
                       ));
                       return builder.build();
                     });
      } else {
        return Single.just(builder.setFullScreenIntent(pendingIntent, true).build());
      }
    } else if (type == TYPE_OUTGOING_RINGING) {
      builder.setContentText(context.getString(R.string.NotificationBarManager__establishing_signal_call));
      builder.addAction(getServiceNotificationAction(context, WebRtcCallService.hangupIntent(context), R.drawable.ic_call_end_grey600_32dp, R.string.NotificationBarManager__cancel_call));
      return Single.just(builder.build());
    } else {
      builder.setContentText(context.getString(R.string.NotificationBarManager_signal_call_in_progress));
      builder.setOnlyAlertOnce(true);
      builder.setPriority(NotificationCompat.PRIORITY_HIGH);
      builder.setCategory(NotificationCompat.CATEGORY_CALL);

      return Single.fromCallable(() -> ConversationUtil.buildPerson(context, recipient))
                   .subscribeOn(Schedulers.io())
                   .observeOn(AndroidSchedulers.mainThread())
                   .map(person -> {
                     builder.setStyle(NotificationCompat.CallStyle.forOngoingCall(
                         person,
                         getServicePendingIntent(context, WebRtcCallService.hangupIntent(context))
                     ));
                     return builder.build();
                   });
    }
  }

  public static int getNotificationId(int type) {
    if (deviceVersionSupportsIncomingCallStyle() && type == TYPE_INCOMING_RINGING) {
      return WEBRTC_NOTIFICATION_RINGING;
    } else {
      return WEBRTC_NOTIFICATION;
    }
  }

  public static @NonNull Notification getStoppingNotification(@NonNull Context context) {
    Intent contentIntent = new Intent(context, MainActivity.class);
    contentIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

    PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, contentIntent, PendingIntentFlags.mutable());

    return new NotificationCompat.Builder(context, NotificationChannels.getInstance().CALL_STATUS).setSmallIcon(R.drawable.ic_call_secure_white_24dp)
                                                                                                  .setContentIntent(pendingIntent)
                                                                                                  .setOngoing(true)
                                                                                                  .setContentTitle(context.getString(R.string.NotificationBarManager__stopping_signal_call_service))
                                                                                                  .setPriority(NotificationCompat.PRIORITY_MIN)
                                                                                                  .build();
  }

  public static int getStartingStoppingNotificationId() {
    return WEBRTC_NOTIFICATION;
  }

  @SuppressWarnings("BooleanMethodIsAlwaysInverted")
  public static boolean isWebRtcNotification(int notificationId) {
    return notificationId == WEBRTC_NOTIFICATION || notificationId == WEBRTC_NOTIFICATION_RINGING;
  }

  private static @NonNull String getNotificationChannel(int type) {
    if ((deviceVersionSupportsIncomingCallStyle() && type == TYPE_INCOMING_RINGING) || type == TYPE_ESTABLISHED) {
      return NotificationChannels.getInstance().CALLS;
    } else {
      return NotificationChannels.getInstance().CALL_STATUS;
    }
  }

  private static PendingIntent getServicePendingIntent(@NonNull Context context, @NonNull Intent intent) {
    return Build.VERSION.SDK_INT >= 26 ? PendingIntent.getForegroundService(context, 0, intent, PendingIntentFlags.mutable())
                                       : PendingIntent.getService(context, 0, intent, PendingIntentFlags.mutable());
  }

  private static NotificationCompat.Action getServiceNotificationAction(Context context, Intent intent, int iconResId, int titleResId) {
    return new NotificationCompat.Action(iconResId, context.getString(titleResId), getServicePendingIntent(context, intent));
  }

  private static PendingIntent getActivityPendingIntent(@NonNull Context context, @NonNull String action) {
    Intent intent = new Intent(context, WebRtcCallActivity.class);
    intent.setAction(action);

    return PendingIntent.getActivity(context, 0, intent, PendingIntentFlags.mutable());
  }

  private static boolean deviceVersionSupportsIncomingCallStyle() {
    return Build.VERSION.SDK_INT >= API_LEVEL_CALL_STYLE;
  }
}
