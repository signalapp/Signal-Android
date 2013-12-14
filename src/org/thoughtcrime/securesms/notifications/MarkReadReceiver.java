package org.thoughtcrime.securesms.notifications;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;

public class MarkReadReceiver extends BroadcastReceiver {

  public static final String CLEAR_ACTION = "org.thoughtcrime.securesms.notifications.CLEAR";

  @Override
  public void onReceive(final Context context, Intent intent) {
    if (!intent.getAction().equals(CLEAR_ACTION))
      return;

    final MasterSecret masterSecret = intent.getParcelableExtra("master_secret");

    if (masterSecret != null) {

      ((NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE))
          .cancel(MessageNotifier.NOTIFICATION_ID);

      new AsyncTask<Void, Void, Void>() {
        @Override
        protected Void doInBackground(Void... params) {
          DatabaseFactory.getThreadDatabase(context).setAllThreadsRead();

          MessageNotifier.updateNotification(context, masterSecret);
          return null;
        }
      }.execute();
    }
  }
}
