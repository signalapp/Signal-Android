/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.annimon.stream.Stream;

import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.MessageTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.ThreadTable;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.ReactionRecord;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.notifications.MarkReadReceiver;
import org.thoughtcrime.securesms.notifications.v2.ConversationId;
import org.thoughtcrime.securesms.util.Debouncer;
import org.thoughtcrime.securesms.util.concurrent.SerialMonoLifoExecutor;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

public class MarkReadHelper {
  private static final String TAG = Log.tag(MarkReadHelper.class);

  private static final long     DEBOUNCE_TIMEOUT = 100;
  private static final Executor EXECUTOR         = new SerialMonoLifoExecutor(SignalExecutors.BOUNDED);

  private final ConversationId conversationId;
  private final Context        context;
  private final LifecycleOwner lifecycleOwner;
  private final Debouncer      debouncer         = new Debouncer(DEBOUNCE_TIMEOUT);
  private       long           latestTimestamp;
  private       boolean        ignoreViewReveals = false;

  public MarkReadHelper(@NonNull ConversationId conversationId, @NonNull Context context, @NonNull LifecycleOwner lifecycleOwner) {
    this.conversationId = conversationId;
    this.context        = context.getApplicationContext();
    this.lifecycleOwner = lifecycleOwner;
  }

  public void onViewsRevealed(long timestamp) {
    if (timestamp <= latestTimestamp || lifecycleOwner.getLifecycle().getCurrentState() != Lifecycle.State.RESUMED || ignoreViewReveals) {
      return;
    }

    latestTimestamp = timestamp;

    debouncer.publish(() -> {
      EXECUTOR.execute(() -> {
        ThreadTable                          threadTable = SignalDatabase.threads();
        List<MessageTable.MarkedMessageInfo> infos       = threadTable.setReadSince(conversationId, false, timestamp);

        Log.d(TAG, "Marking " + infos.size() + " messages as read.");

        AppDependencies.getMessageNotifier().updateNotification(context);
        MarkReadReceiver.process(infos);
        MarkReadReceiver.processCallEvents(Collections.singletonList(conversationId), timestamp);
      });
    });
  }

  /**
   * Prevent calls to {@link #onViewsRevealed(long)} from causing messages to be marked read.
   * <p>
   * This is particularly useful when the conversation could move around after views are
   * displayed (e.g., initial scrolling to start position).
   */
  public void ignoreViewReveals() {
    ignoreViewReveals = true;
  }

  /**
   * Stop preventing calls to {@link #onViewsRevealed(long)} from not marking messages as read.
   *
   * @param timestamp Timestamp of most recent reveal messages, same as usually provided to {@code onViewsRevealed}
   */
  public void stopIgnoringViewReveals(@Nullable Long timestamp) {
    this.ignoreViewReveals = false;
    if (timestamp != null) {
      onViewsRevealed(timestamp);
    }
  }

  /**
   * Given the adapter and manager, figure out the timestamp to mark read up to.
   *
   * @param conversationAdapter The conversation thread's adapter
   * @param layoutManager       The conversation thread's layout manager
   * @return A Present(Long) if there's a timestamp to proceed with, or Empty if this request should be ignored.
   */
  @SuppressWarnings("resource")
  public static @NonNull Optional<Long> getLatestTimestamp(@NonNull ConversationAdapterBridge conversationAdapter,
                                                           @NonNull LinearLayoutManager layoutManager)
  {
    if (conversationAdapter.hasNoConversationMessages()) {
      return Optional.empty();
    }

    int position = layoutManager.findFirstVisibleItemPosition();
    if (position == -1 || position == layoutManager.getItemCount() - 1) {
      return Optional.empty();
    }

    ConversationMessage item = conversationAdapter.getConversationMessage(position);
    if (item == null) {
      item = conversationAdapter.getConversationMessage(position + 1);
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
