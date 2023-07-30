package org.thoughtcrime.securesms.notifications;

import android.app.Application;
import android.content.Context;
import android.os.Handler;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.ExceptionUtil;
import org.signal.core.util.ThreadUtil;
import org.signal.core.util.concurrent.SignalExecutors;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.notifications.v2.DefaultMessageNotifier;
import org.thoughtcrime.securesms.notifications.v2.ConversationId;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.BubbleUtil;
import org.thoughtcrime.securesms.util.LeakyBucketLimiter;

import java.util.Optional;

/**
 * Uses a leaky-bucket strategy to limiting notification updates.
 */
public class OptimizedMessageNotifier implements MessageNotifier {

  private final LeakyBucketLimiter     limiter;
  private final DefaultMessageNotifier defaultMessageNotifier;

  private static final String DEDUPE_KEY_GENERAL        = "MESSAGE_NOTIFIER_DEFAULT";
  private static final String DEDUPE_KEY_CHAT           = "MESSAGE_NOTIFIER_CHAT_";
  private static final String DEDUPE_KEY_CANCEL_DELAYED = "MESSAGE_NOTIFIER_CANCEL_DELAYED";

  @MainThread
  public OptimizedMessageNotifier(@NonNull Application context) {
    this.limiter                = new LeakyBucketLimiter(3, 1000, new Handler(SignalExecutors.getAndStartHandlerThread("signal-notifier", ThreadUtil.PRIORITY_IMPORTANT_BACKGROUND_THREAD).getLooper()));
    this.defaultMessageNotifier = new DefaultMessageNotifier(context);
  }

  @Override
  public void setVisibleThread(@Nullable ConversationId conversationId) {
    getNotifier().setVisibleThread(conversationId);
  }

  @Override
  public @NonNull Optional<ConversationId> getVisibleThread() {
    return getNotifier().getVisibleThread();
  }

  @Override
  public void clearVisibleThread() {
    getNotifier().clearVisibleThread();
  }

  @Override
  public void setLastDesktopActivityTimestamp(long timestamp) {
    getNotifier().setLastDesktopActivityTimestamp(timestamp);
  }

  @Override
  public void notifyMessageDeliveryFailed(@NonNull Context context, @NonNull Recipient recipient, @NonNull ConversationId conversationId) {
    SignalDatabase.runPostSuccessfulTransaction(() -> {
      getNotifier().notifyMessageDeliveryFailed(context, recipient, conversationId);
    });
  }

  @Override
  public void notifyStoryDeliveryFailed(@NonNull Context context, @NonNull Recipient recipient, @NonNull ConversationId conversationId) {
    SignalDatabase.runPostSuccessfulTransaction(() -> {
      getNotifier().notifyStoryDeliveryFailed(context, recipient, conversationId);
    });
  }

  @Override
  public void notifyProofRequired(@NonNull Context context, @NonNull Recipient recipient, @NonNull ConversationId conversationId) {
    SignalDatabase.runPostSuccessfulTransaction(() -> {
      getNotifier().notifyProofRequired(context, recipient, conversationId);
    });
  }

  @Override
  public void cancelDelayedNotifications() {
    SignalDatabase.runPostSuccessfulTransaction(DEDUPE_KEY_CANCEL_DELAYED, () -> {
      getNotifier().cancelDelayedNotifications();
    });
  }

  @Override
  public void updateNotification(@NonNull Context context) {
    SignalDatabase.runPostSuccessfulTransaction(DEDUPE_KEY_GENERAL, () -> {
      runOnLimiter(() -> getNotifier().updateNotification(context));
    });
  }

  @Override
  public void updateNotification(@NonNull Context context, @NonNull ConversationId conversationId) {
    SignalDatabase.runPostSuccessfulTransaction(DEDUPE_KEY_CHAT + conversationId.getThreadId(), () -> {
      runOnLimiter(() -> getNotifier().updateNotification(context, conversationId));
    });
  }

  @Override
  public void updateNotification(@NonNull Context context, @NonNull ConversationId conversationId, @NonNull BubbleUtil.BubbleState defaultBubbleState) {
    SignalDatabase.runPostSuccessfulTransaction(() -> {
      runOnLimiter(() -> getNotifier().updateNotification(context, conversationId, defaultBubbleState));
    });
  }

  @Override
  public void updateNotification(@NonNull Context context, @NonNull ConversationId conversationId, boolean signal) {
    SignalDatabase.runPostSuccessfulTransaction(() -> {
      runOnLimiter(() -> getNotifier().updateNotification(context, conversationId, signal));
    });
  }

  @Override
  public void updateNotification(@NonNull Context context, @Nullable ConversationId conversationId, boolean signal, int reminderCount, @NonNull BubbleUtil.BubbleState defaultBubbleState) {
    SignalDatabase.runPostSuccessfulTransaction(() -> {
      runOnLimiter(() -> getNotifier().updateNotification(context, conversationId, signal, reminderCount, defaultBubbleState));
    });
  }

  @Override
  public void clearReminder(@NonNull Context context) {
    SignalDatabase.runPostSuccessfulTransaction(() -> {
      getNotifier().clearReminder(context);
    });
  }

  @Override
  public void addStickyThread(@NonNull ConversationId conversationId, long earliestTimestamp) {
    SignalDatabase.runPostSuccessfulTransaction(() -> {
      getNotifier().addStickyThread(conversationId, earliestTimestamp);
    });
  }

  @Override
  public void removeStickyThread(@NonNull ConversationId conversationId) {
    SignalDatabase.runPostSuccessfulTransaction(() -> {
      getNotifier().removeStickyThread(conversationId);
    });
  }

  private void runOnLimiter(@NonNull Runnable runnable) {
    Throwable prettyException = new Throwable();
    limiter.run(() -> {
      try {
        runnable.run();
      } catch (RuntimeException e) {
        throw ExceptionUtil.joinStackTrace(e, prettyException);
      }
    });
  }

  private MessageNotifier getNotifier() {
    return defaultMessageNotifier;
  }
}
