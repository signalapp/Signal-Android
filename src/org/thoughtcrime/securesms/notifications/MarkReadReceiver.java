package org.thoughtcrime.securesms.notifications;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.support.annotation.Nullable;
import android.util.Log;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;

import java.util.HashSet;
import java.util.Set;

public class MarkReadReceiver extends MasterSecretBroadcastReceiver {

  private static final String TAG              = MarkReadReceiver.class.getSimpleName();
  public static final  String CLEAR_ACTION     = "org.thoughtcrime.securesms.notifications.CLEAR";
  public static final  String THREAD_IDS_EXTRA = "thread_ids";

  @Override
  protected void onReceive(final Context context, Intent intent,
                           @Nullable final MasterSecret masterSecret)
  {
    if (!CLEAR_ACTION.equals(intent.getAction()))
      return;

    final long[] threadIds = intent.getLongArrayExtra(THREAD_IDS_EXTRA);

    if (threadIds != null) {
      Log.w(TAG, "threadIds length: " + threadIds.length);

      new AsyncTask<Void, Void, Void>() {
        @Override
        protected Void doInBackground(Void... params) {
          Set<Long> threadIdsAsSet = new HashSet<Long>();

          for (long threadId : threadIds) {
            Log.w(TAG, "Marking as read: " + threadId);
            DatabaseFactory.getThreadDatabase(context).setRead(threadId);
            threadIdsAsSet.add(threadId);
          }

          MessageNotifier.updateNotificationCancelRead(context, masterSecret, threadIdsAsSet);
          return null;
        }
      }.execute();
    }
  }
}
