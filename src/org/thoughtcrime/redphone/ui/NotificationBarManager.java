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

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.v4.app.NotificationCompat;

import org.thoughtcrime.redphone.RedPhone;
import org.thoughtcrime.securesms.ConversationActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.Recipients;

/**
 * Manages the state of the RedPhone items in the Android notification bar.
 *
 * @author Moxie Marlinspike
 *
 */

public class NotificationBarManager {

  private static final int RED_PHONE_NOTIFICATION   = 313388;
  private static final int MISSED_CALL_NOTIFICATION = 313389;

  public static void setCallEnded(Context context) {
    NotificationManager notificationManager = (NotificationManager)context
        .getSystemService(Context.NOTIFICATION_SERVICE);

    notificationManager.cancel(RED_PHONE_NOTIFICATION);
  }

  public static void setCallInProgress(Context context) {
    NotificationManager notificationManager = (NotificationManager)context
        .getSystemService(Context.NOTIFICATION_SERVICE);

    Intent contentIntent        = new Intent(context, RedPhone.class);
    contentIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
    PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, contentIntent, 0);
    String notificationText     = context.getString(R.string.NotificationBarManager_signal_call_in_progress);
    Notification notification   = new Notification(R.drawable.redphone_stat_sys_phone_call, null,
                                                   System.currentTimeMillis());

    notification.setLatestEventInfo(context, notificationText, notificationText, pendingIntent);
    notification.flags = Notification.FLAG_NO_CLEAR;
    notificationManager.notify(RED_PHONE_NOTIFICATION, notification);
  }
  
}
