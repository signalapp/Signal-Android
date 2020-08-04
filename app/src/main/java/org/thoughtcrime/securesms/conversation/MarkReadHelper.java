package org.thoughtcrime.securesms.conversation;

import android.content.Context;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MessagingDatabase;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.notifications.MarkReadReceiver;
import org.thoughtcrime.securesms.util.Debouncer;
import org.thoughtcrime.securesms.util.concurrent.SerialMonoLifoExecutor;
import org.thoughtcrime.securesms.util.concurrent.SignalExecutors;

import java.util.List;
import java.util.concurrent.Executor;

class MarkReadHelper {
  private static final String TAG = Log.tag(MarkReadHelper.class);

  private static final long     DEBOUNCE_TIMEOUT = 100;
  private static final Executor EXECUTOR         = new SerialMonoLifoExecutor(SignalExecutors.BOUNDED);

  private final long      threadId;
  private final Context   context;
  private final Debouncer debouncer = new Debouncer(DEBOUNCE_TIMEOUT);
  private       long      latestTimestamp;

  MarkReadHelper(long threadId, @NonNull Context context) {
    this.threadId = threadId;
    this.context  = context.getApplicationContext();
  }

  public void onViewsRevealed(long timestamp) {
    if (timestamp <= latestTimestamp) {
      return;
    }

    latestTimestamp = timestamp;

    debouncer.publish(() -> {
      EXECUTOR.execute(() -> {
        ThreadDatabase                            threadDatabase = DatabaseFactory.getThreadDatabase(context);
        List<MessagingDatabase.MarkedMessageInfo> infos          = threadDatabase.setReadSince(threadId, false, timestamp);

        Log.d(TAG, "Marking " + infos.size() + " messages as read.");

        ApplicationDependencies.getMessageNotifier().updateNotification(context);
        MarkReadReceiver.process(context, infos);
      });
    });
  }
}
