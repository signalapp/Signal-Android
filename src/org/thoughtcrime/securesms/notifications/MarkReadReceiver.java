/**
 * Copyright (C) 2013-2014 Open WhisperSystems
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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.util.Log;

import org.whispersystems.textsecure.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;

public class MarkReadReceiver extends BroadcastReceiver {

  public static final String CLEAR_ACTION = "org.thoughtcrime.securesms.notifications.CLEAR";

  @Override
  public void onReceive(final Context context, Intent intent) {
    if (!intent.getAction().equals(CLEAR_ACTION))
      return;

    final long[]       threadIds    = intent.getLongArrayExtra("thread_ids");
    final MasterSecret masterSecret = intent.getParcelableExtra("master_secret");

    if (threadIds != null && masterSecret != null) {
      Log.w("MarkReadReceiver", "threadIds length: " + threadIds.length);

      ((NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE))
          .cancel(MessageNotifier.NOTIFICATION_ID);

      new AsyncTask<Void, Void, Void>() {
        @Override
        protected Void doInBackground(Void... params) {
          for (long threadId : threadIds) {
            Log.w("MarkReadReceiver", "Marking as read: " + threadId);
            DatabaseFactory.getThreadDatabase(context).setRead(threadId);
          }

          MessageNotifier.updateNotification(context, masterSecret);
          return null;
        }
      }.execute();
    }
  }
}
