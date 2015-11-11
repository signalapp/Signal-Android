/*
 * Copyright (C) 2012 Moxie Marlinspike
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.thoughtcrime.redphone.ui;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;

import org.thoughtcrime.redphone.RedPhone;
import org.thoughtcrime.securesms.R;

/**
 * Manages the state of the RedPhone items in the Android notification bar.
 *
 * @author Moxie Marlinspike
 *
 */

public class NotificationBarManager {

  private static final int RED_PHONE_NOTIFICATION   = 313388;
  private static final int MISSED_CALL_NOTIFICATION = 313389;

  public static final int TYPE_INCOMING_RINGING = 1;
  public static final int TYPE_OUTGOING_RINGING = 2;
  public static final int TYPE_ESTABLISHED      = 3;

  public static void setCallEnded(Context context) {
    NotificationManager notificationManager = (NotificationManager)context
        .getSystemService(Context.NOTIFICATION_SERVICE);

    notificationManager.cancel(RED_PHONE_NOTIFICATION);
  }

  public static void setCallInProgress(Context context, int type) {
    NotificationManager notificationManager = (NotificationManager)context
        .getSystemService(Context.NOTIFICATION_SERVICE);

    Intent contentIntent        = new Intent(context, RedPhone.class);
    contentIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
    PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, contentIntent, 0);

    NotificationCompat.Builder builder = new NotificationCompat.Builder(context)
                                                               .setSmallIcon(R.drawable.redphone_stat_sys_phone_call)
                                                               .setContentIntent(pendingIntent)
                                                               .setOngoing(true);

    if (type == TYPE_INCOMING_RINGING) {
      builder.setContentTitle(context.getString(R.string.NotificationBarManager__incoming_signal_call));
      builder.setContentText(context.getString(R.string.NotificationBarManager__incoming_signal_call));
      builder.addAction(getNotificationAction(context, RedPhone.DENY_ACTION, R.drawable.ic_close,   R.string.NotificationBarManager__deny_call));
      builder.addAction(getNotificationAction(context, RedPhone.ANSWER_ACTION, R.drawable.ic_phone, R.string.NotificationBarManager__answer_call));
    } else if (type == TYPE_OUTGOING_RINGING) {
      builder.setContentTitle(context.getString(R.string.NotificationBarManager_signal_call_in_progress));
      builder.setContentText(context.getString(R.string.NotificationBarManager_signal_call_in_progress));
      builder.addAction(getNotificationAction(context, RedPhone.END_CALL_ACTION, R.drawable.ic_call_end, R.string.NotificationBarManager__cancel_call));
    } else {
      builder.setContentTitle(context.getString(R.string.NotificationBarManager_signal_call_in_progress));
      builder.setContentText(context.getString(R.string.NotificationBarManager_signal_call_in_progress));
      builder.addAction(getNotificationAction(context, RedPhone.END_CALL_ACTION, R.drawable.ic_call_end, R.string.NotificationBarManager__end_call));
    }

    notificationManager.notify(RED_PHONE_NOTIFICATION, builder.build());
  }

  private static NotificationCompat.Action getNotificationAction(Context context, String action, int iconResId, int titleResId) {
    Intent intent = new Intent(context, RedPhone.class);
    intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
    intent.setAction(action);
    PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);
    return new NotificationCompat.Action(iconResId, context.getString(titleResId), pendingIntent);
  }
}
