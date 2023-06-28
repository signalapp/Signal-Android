package org.thoughtcrime.securesms.webrtc;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.Person;

import org.signal.core.util.PendingIntentFlags;
import org.thoughtcrime.securesms.MainActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.WebRtcCallActivity;
import org.thoughtcrime.securesms.notifications.NotificationChannels;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.service.webrtc.WebRtcCallService;
import org.thoughtcrime.securesms.util.ConversationUtil;

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

  private enum LaunchCallScreenIntentState {
    CONTENT(null, 0),
    AUDIO(WebRtcCallActivity.ANSWER_ACTION, 1),
    VIDEO(WebRtcCallActivity.ANSWER_VIDEO_ACTION, 2);

    final @Nullable String action;
    final int              requestCode;

    LaunchCallScreenIntentState(@Nullable String action, int requestCode) {
      this.action      = action;
      this.requestCode = requestCode;
    }
  }

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

  public static Notification getCallInProgressNotification(Context context, int type, Recipient recipient, boolean isVideoCall) {
    PendingIntent              pendingIntent = getActivityPendingIntent(context, LaunchCallScreenIntentState.CONTENT);
    NotificationCompat.Builder builder       = new NotificationCompat.Builder(context, getNotificationChannel(type))
        .setSmallIcon(R.drawable.ic_call_secure_white_24dp)
        .setContentIntent(pendingIntent)
        .setOngoing(true)
        .setContentTitle(recipient.getDisplayName(context));

    if (type == TYPE_INCOMING_CONNECTING) {
      builder.setContentText(context.getString(R.string.CallNotificationBuilder_connecting));
      builder.setPriority(NotificationCompat.PRIORITY_MIN);
      builder.setContentIntent(null);
      return builder.build();
    } else if (type == TYPE_INCOMING_RINGING) {
      builder.setContentText(getIncomingCallContentText(context, recipient, isVideoCall));
      builder.setPriority(NotificationCompat.PRIORITY_HIGH);
      builder.setCategory(NotificationCompat.CATEGORY_CALL);
      builder.setFullScreenIntent(pendingIntent, true);

      if (deviceVersionSupportsIncomingCallStyle()) {
        Person person = ConversationUtil.buildPerson(context, recipient);
        builder.setStyle(NotificationCompat.CallStyle.forIncomingCall(
            person,
            getServicePendingIntent(context, WebRtcCallService.denyCallIntent(context)),
            getActivityPendingIntent(context, isVideoCall ? LaunchCallScreenIntentState.VIDEO : LaunchCallScreenIntentState.AUDIO)
        ).setIsVideo(isVideoCall));
      }

      return builder.build();
    } else if (type == TYPE_OUTGOING_RINGING) {
      builder.setContentText(context.getString(R.string.NotificationBarManager__establishing_signal_call));
      builder.addAction(getServiceNotificationAction(context, WebRtcCallService.hangupIntent(context), R.drawable.ic_call_end_grey600_32dp, R.string.NotificationBarManager__cancel_call));
      return builder.build();
    } else {
      builder.setContentText(getOngoingCallContentText(context, recipient, isVideoCall));
      builder.setOnlyAlertOnce(true);
      builder.setPriority(NotificationCompat.PRIORITY_DEFAULT);
      builder.setCategory(NotificationCompat.CATEGORY_CALL);

      if (deviceVersionSupportsIncomingCallStyle()) {
        Person person = ConversationUtil.buildPerson(context, recipient);
        builder.setStyle(NotificationCompat.CallStyle.forOngoingCall(
            person,
            getServicePendingIntent(context, WebRtcCallService.hangupIntent(context))
        ).setIsVideo(isVideoCall));
      }

      return builder.build();
    }
  }

  public static int getNotificationId(int type) {
    if (deviceVersionSupportsIncomingCallStyle() && type == TYPE_INCOMING_RINGING) {
      return WEBRTC_NOTIFICATION_RINGING;
    } else {
      return WEBRTC_NOTIFICATION;
    }
  }

  public static @NonNull Notification getStartingNotification(@NonNull Context context) {
    return new NotificationCompat.Builder(context, NotificationChannels.getInstance().CALL_STATUS)
                                 .setSmallIcon(R.drawable.ic_call_secure_white_24dp)
                                 .setOngoing(true)
                                 .setContentTitle(context.getString(R.string.NotificationBarManager__starting_signal_call_service))
                                 .setPriority(NotificationCompat.PRIORITY_MIN)
                                 .build();
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

  private static @NonNull String getIncomingCallContentText(@NonNull Context context, @NonNull Recipient recipient, boolean isVideoCall) {
    if (recipient.isGroup()) {
      return context.getString(R.string.CallNotificationBuilder__incoming_signal_group_call);
    } else if (isVideoCall) {
      return context.getString(R.string.CallNotificationBuilder__incoming_signal_video_call);
    } else {
      return context.getString(R.string.CallNotificationBuilder__incoming_signal_voice_call);
    }
  }

  private static @NonNull String getOngoingCallContentText(@NonNull Context context, @NonNull Recipient recipient, boolean isVideoCall) {
    if (recipient.isGroup()) {
      return context.getString(R.string.CallNotificationBuilder__ongoing_signal_group_call);
    } else if (isVideoCall) {
      return context.getString(R.string.CallNotificationBuilder__ongoing_signal_video_call);
    } else {
      return context.getString(R.string.CallNotificationBuilder__ongoing_signal_voice_call);
    }
  }

  private static @NonNull String getNotificationChannel(int type) {
    if (type == TYPE_INCOMING_RINGING) {
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

  private static PendingIntent getActivityPendingIntent(@NonNull Context context, @NonNull LaunchCallScreenIntentState launchCallScreenIntentState) {
    Intent intent = new Intent(context, WebRtcCallActivity.class);
    intent.setAction(launchCallScreenIntentState.action);

    if (launchCallScreenIntentState == LaunchCallScreenIntentState.CONTENT) {
      intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
    }

    intent.putExtra(WebRtcCallActivity.EXTRA_STARTED_FROM_FULLSCREEN, launchCallScreenIntentState == LaunchCallScreenIntentState.CONTENT);
    intent.putExtra(WebRtcCallActivity.EXTRA_ENABLE_VIDEO_IF_AVAILABLE, false);

    return PendingIntent.getActivity(context, launchCallScreenIntentState.requestCode, intent, PendingIntentFlags.updateCurrent());
  }

  private static boolean deviceVersionSupportsIncomingCallStyle() {
    return Build.VERSION.SDK_INT >= API_LEVEL_CALL_STYLE;
  }
}
