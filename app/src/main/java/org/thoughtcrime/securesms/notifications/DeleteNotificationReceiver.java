package org.thoughtcrime.securesms.notifications;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;

import org.thoughtcrime.securesms.database.DatabaseFactory;

public class DeleteNotificationReceiver extends BroadcastReceiver {

  public static String DELETE_NOTIFICATION_ACTION = "org.thoughtcrime.securesms.DELETE_NOTIFICATION";

  public static String EXTRA_IDS = "message_ids";
  public static String EXTRA_MMS = "is_mms";

  @Override
  public void onReceive(final Context context, Intent intent) {
    if (DELETE_NOTIFICATION_ACTION.equals(intent.getAction())) {
      MessageNotifier.clearReminder(context);

      final long[]    ids = intent.getLongArrayExtra(EXTRA_IDS);
      final boolean[] mms = intent.getBooleanArrayExtra(EXTRA_MMS);

      if (ids == null  || mms == null || ids.length != mms.length) return;

      new AsyncTask<Void, Void, Void>() {
        @Override
        protected Void doInBackground(Void... params) {
          for (int i=0;i<ids.length;i++) {
            if (!mms[i]) DatabaseFactory.getSmsDatabase(context).markAsNotified(ids[i]);
            else         DatabaseFactory.getMmsDatabase(context).markAsNotified(ids[i]);
          }

          return null;
        }
      }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
  }
}
