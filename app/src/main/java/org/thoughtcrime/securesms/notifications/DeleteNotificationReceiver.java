package org.thoughtcrime.securesms.notifications;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.signal.core.util.concurrent.SignalExecutors;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.notifications.v2.ConversationId;

import java.util.ArrayList;

public class DeleteNotificationReceiver extends BroadcastReceiver {

  public static String DELETE_NOTIFICATION_ACTION = "org.thoughtcrime.securesms.DELETE_NOTIFICATION";

  public static final String EXTRA_MAX_MESSAGE_ID = "max_message_id";
  public static final String EXTRA_THREADS        = "threads";

  @Override
  public void onReceive(final Context context, Intent intent) {
    if (DELETE_NOTIFICATION_ACTION.equals(intent.getAction())) {
      MessageNotifier notifier = AppDependencies.getMessageNotifier();

      final long                      maxMessageId = intent.getLongExtra(EXTRA_MAX_MESSAGE_ID, 0);
      final ArrayList<ConversationId> threads      = intent.getParcelableArrayListExtra(EXTRA_THREADS);

      if (threads != null) {
        for (ConversationId thread : threads) {
          notifier.removeStickyThread(thread);
        }
      }

      if (threads == null || threads.isEmpty() || maxMessageId <= 0) return;

      PendingResult finisher = goAsync();

      SignalExecutors.BOUNDED.execute(() -> {
        SignalDatabase.messages().markConversationsAsNotified(threads, maxMessageId);
        finisher.finish();
      });
    }
  }
}
