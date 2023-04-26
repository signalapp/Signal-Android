package org.thoughtcrime.securesms.conversation;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;

import com.annimon.stream.Stream;

import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.components.recyclerview.SmoothScrollingLinearLayoutManager;
import org.thoughtcrime.securesms.database.MessageTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.ThreadTable;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.ReactionRecord;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.notifications.MarkReadReceiver;
import org.thoughtcrime.securesms.notifications.v2.ConversationId;
import org.thoughtcrime.securesms.util.Debouncer;
import org.thoughtcrime.securesms.util.concurrent.SerialMonoLifoExecutor;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

public class MarkReadHelper {
  private static final String TAG = Log.tag(MarkReadHelper.class);

  private static final long     DEBOUNCE_TIMEOUT = 100;
  private static final Executor EXECUTOR         = new SerialMonoLifoExecutor(SignalExecutors.BOUNDED);

  private final ConversationId conversationId;
  private final Context        context;
  private final LifecycleOwner     lifecycleOwner;
  private final Debouncer          debouncer = new Debouncer(DEBOUNCE_TIMEOUT);
  private       long               latestTimestamp;

  public MarkReadHelper(@NonNull ConversationId conversationId, @NonNull Context context, @NonNull LifecycleOwner lifecycleOwner) {
    this.conversationId = conversationId;
    this.context        = context.getApplicationContext();
    this.lifecycleOwner = lifecycleOwner;
  }

  public void onViewsRevealed(long timestamp) {
    if (timestamp <= latestTimestamp || lifecycleOwner.getLifecycle().getCurrentState() != Lifecycle.State.RESUMED) {
      return;
    }

    latestTimestamp = timestamp;

    debouncer.publish(() -> {
      EXECUTOR.execute(() -> {
        ThreadTable                          threadTable = SignalDatabase.threads();
        List<MessageTable.MarkedMessageInfo> infos       = threadTable.setReadSince(conversationId, false, timestamp);

        Log.d(TAG, "Marking " + infos.size() + " messages as read.");

        ApplicationDependencies.getMessageNotifier().updateNotification(context);
        MarkReadReceiver.process(context, infos);
      });
    });
  }

  /**
   * Given the adapter and manager, figure out the timestamp to mark read up to.
   *
   * @param conversationAdapter   The conversation thread's adapter
   * @param layoutManager         The conversation thread's layout manager
   * @return A Present(Long) if there's a timestamp to proceed with, or Empty if this request should be ignored.
   */
  @SuppressWarnings("resource")
  public static @NonNull Optional<Long> getLatestTimestamp(@NonNull ConversationAdapter conversationAdapter,
                                                           @NonNull SmoothScrollingLinearLayoutManager layoutManager)
  {
    if (conversationAdapter.hasNoConversationMessages()) {
      return Optional.empty();
    }

    int position = layoutManager.findFirstVisibleItemPosition();
    if (position == -1 || position == layoutManager.getItemCount() - 1) {
      return Optional.empty();
    }

    ConversationMessage item = conversationAdapter.getItem(position);
    if (item == null) {
      item = conversationAdapter.getItem(position + 1);
    }

    if (item != null) {
      MessageRecord record = item.getMessageRecord();
      long latestReactionReceived = Stream.of(record.getReactions())
                                          .map(ReactionRecord::getDateReceived)
                                          .max(Long::compareTo)
                                          .orElse(0L);

      return Optional.of(Math.max(record.getDateReceived(), latestReactionReceived));
    }

    return Optional.empty();
  }
}
