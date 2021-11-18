package org.thoughtcrime.securesms.notifications;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.signal.core.util.concurrent.SignalExecutors;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;

public class DeleteNotificationReceiver extends BroadcastReceiver {

  public static String DELETE_NOTIFICATION_ACTION = "org.thoughtcrime.securesms.DELETE_NOTIFICATION";

  public static final String EXTRA_IDS        = "message_ids";
  public static final String EXTRA_MMS        = "is_mms";
  public static final String EXTRA_THREAD_IDS = "thread_ids";

  @Override
  public void onReceive(final Context context, Intent intent) {
    if (DELETE_NOTIFICATION_ACTION.equals(intent.getAction())) {
      MessageNotifier notifier = ApplicationDependencies.getMessageNotifier();
      notifier.clearReminder(context);

      final long[]    ids       = intent.getLongArrayExtra(EXTRA_IDS);
      final boolean[] mms       = intent.getBooleanArrayExtra(EXTRA_MMS);
      final long[]    threadIds = intent.getLongArrayExtra(EXTRA_THREAD_IDS);

      if (threadIds != null) {
        for (long threadId : threadIds) {
          notifier.removeStickyThread(threadId);
        }
      }

      if (ids == null || mms == null || ids.length != mms.length) return;

      PendingResult finisher = goAsync();

      SignalExecutors.BOUNDED.execute(() -> {
        for (int i = 0; i < ids.length; i++) {
          if (!mms[i]) {
            SignalDatabase.sms().markAsNotified(ids[i]);
          } else {
            SignalDatabase.mms().markAsNotified(ids[i]);
          }
        }
        finisher.finish();
      });
    }
  }
}
