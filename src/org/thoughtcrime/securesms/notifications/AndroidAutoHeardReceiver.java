/**
 * Copyright (C) 2011 Whisper Systems
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

package org.thoughtcrime.securesms.notifications;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationManagerCompat;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MessagingDatabase.MarkedMessageInfo;
import org.whispersystems.libsignal.logging.Log;

import java.util.LinkedList;
import java.util.List;

/**
 * Marks an Android Auto as read after the driver have listened to it
 */
public class AndroidAutoHeardReceiver extends MasterSecretBroadcastReceiver {

  public static final String TAG                   = AndroidAutoHeardReceiver.class.getSimpleName();
  public static final String HEARD_ACTION          = "org.thoughtcrime.securesms.notifications.ANDROID_AUTO_HEARD";
  public static final String THREAD_IDS_EXTRA      = "car_heard_thread_ids";
  public static final String NOTIFICATION_ID_EXTRA = "car_notification_id";

  @Override
  protected void onReceive(final Context context, Intent intent,
                           @Nullable final MasterSecret masterSecret)
  {
    if (!HEARD_ACTION.equals(intent.getAction()))
      return;

    final long[] threadIds = intent.getLongArrayExtra(THREAD_IDS_EXTRA);

    if (threadIds != null) {
      int notificationId = intent.getIntExtra(NOTIFICATION_ID_EXTRA, -1);
      NotificationManagerCompat.from(context).cancel(notificationId);

      new AsyncTask<Void, Void, Void>() {
        @Override
        protected Void doInBackground(Void... params) {
          List<MarkedMessageInfo> messageIdsCollection = new LinkedList<>();

          for (long threadId : threadIds) {
            Log.i(TAG, "Marking meassage as read: " + threadId);
            List<MarkedMessageInfo> messageIds = DatabaseFactory.getThreadDatabase(context).setRead(threadId, true);

            messageIdsCollection.addAll(messageIds);
          }

          MessageNotifier.updateNotification(context, masterSecret);
          MarkReadReceiver.process(context, messageIdsCollection);

          return null;
        }
      }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
  }
}
