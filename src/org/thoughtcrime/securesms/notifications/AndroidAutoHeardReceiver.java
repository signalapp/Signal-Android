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

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.support.annotation.Nullable;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MessagingDatabase.SyncMessageId;
import org.thoughtcrime.securesms.jobs.MultiDeviceReadUpdateJob;
import org.whispersystems.libsignal.logging.Log;

import java.util.LinkedList;
import java.util.List;

/**
 * Get the response text from the Wearable Device and sends an message as a reply
 */
public class AndroidAutoHeardReceiver extends MasterSecretBroadcastReceiver {

  public static final String TAG                 = AndroidAutoHeardReceiver.class.getSimpleName();
  public static final String HEARD_ACTION        = "org.thoughtcrime.securesms.notifications.ANDROID_AUTO_HEARD";
  public static final String THREAD_IDS_EXTRA     = "car_heard_thread_ids";

  @Override
  protected void onReceive(final Context context, Intent intent,
                           @Nullable final MasterSecret masterSecret)
  {
    if (!HEARD_ACTION.equals(intent.getAction()))
      return;

    final long[] threadIds = intent.getLongArrayExtra(THREAD_IDS_EXTRA);

    if (threadIds != null) {
      new AsyncTask<Void, Void, Void>() {
        @Override
        protected Void doInBackground(Void... params) {
          List<SyncMessageId> messageIdsCollection = new LinkedList<>();

          for (long threadId : threadIds) {
            Log.i(TAG, "Marking meassage as read: " + threadId);
            List<SyncMessageId> messageIds = DatabaseFactory.getThreadDatabase(context).setRead(threadId);
            messageIdsCollection.addAll(messageIds);
          }

          MessageNotifier.updateNotification(context, masterSecret);

          if (!messageIdsCollection.isEmpty()) {
            ApplicationContext.getInstance(context)
                    .getJobManager()
                    .add(new MultiDeviceReadUpdateJob(context, messageIdsCollection));
          }

          ((NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE))
                  .cancel(MessageNotifier.NOTIFICATION_ID);

          return null;
        }
      }.execute();
    }
  }
}
