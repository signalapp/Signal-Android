package org.thoughtcrime.securesms.components.webrtc;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.signal.core.util.PendingIntentFlags;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.WebRtcCallActivity;
import org.thoughtcrime.securesms.notifications.NotificationChannels;
import org.thoughtcrime.securesms.recipients.Recipient;

/**
 * Utility for showing and hiding safety number change notifications during a group call.
 */
public final class GroupCallSafetyNumberChangeNotificationUtil {

  public static final String GROUP_CALLING_NOTIFICATION_TAG = "group_calling";

  private GroupCallSafetyNumberChangeNotificationUtil() {
  }

  public static void showNotification(@NonNull Context context, @NonNull Recipient recipient) {
    Intent contentIntent = new Intent(context, WebRtcCallActivity.class);
    contentIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

    PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, contentIntent, PendingIntentFlags.mutable());

    Notification safetyNumberChangeNotification = new NotificationCompat.Builder(context, NotificationChannels.getInstance().CALLS)
                                                                        .setSmallIcon(R.drawable.ic_notification)
                                                                        .setContentTitle(recipient.getDisplayName(context))
                                                                        .setContentText(context.getString(R.string.GroupCallSafetyNumberChangeNotification__someone_has_joined_this_call_with_a_safety_number_that_has_changed))
                                                                        .setStyle(new NotificationCompat.BigTextStyle().bigText(context.getString(R.string.GroupCallSafetyNumberChangeNotification__someone_has_joined_this_call_with_a_safety_number_that_has_changed)))
                                                                        .setContentIntent(pendingIntent)
                                                                        .build();

    NotificationManagerCompat.from(context).notify(GROUP_CALLING_NOTIFICATION_TAG, recipient.hashCode(), safetyNumberChangeNotification);
  }

  public static void cancelNotification(@NonNull Context context, @NonNull Recipient recipient) {
    NotificationManagerCompat.from(context).cancel(GROUP_CALLING_NOTIFICATION_TAG, recipient.hashCode());
  }
}
