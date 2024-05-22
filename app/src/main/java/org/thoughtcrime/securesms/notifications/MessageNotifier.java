package org.thoughtcrime.securesms.notifications;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.concurrent.SignalExecutors;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.notifications.v2.ConversationId;
import org.thoughtcrime.securesms.recipients.Recipient;

import java.util.Optional;

public interface MessageNotifier {
  void setVisibleThread(@Nullable ConversationId conversationId);
  @NonNull Optional<ConversationId> getVisibleThread();
  void clearVisibleThread();
  void setLastDesktopActivityTimestamp(long timestamp);
  void notifyMessageDeliveryFailed(@NonNull Context context, @NonNull Recipient recipient, @NonNull ConversationId conversationId);
  void notifyStoryDeliveryFailed(@NonNull Context context, @NonNull Recipient recipient, @NonNull ConversationId conversationId);
  void notifyProofRequired(@NonNull Context context, @NonNull Recipient recipient, @NonNull ConversationId conversationId);
  void cancelDelayedNotifications();
  void updateNotification(@NonNull Context context);
  void updateNotification(@NonNull Context context, @NonNull ConversationId conversationId);
  void forceBubbleNotification(@NonNull Context context, @NonNull ConversationId conversationId);
  void addStickyThread(@NonNull ConversationId conversationId, long earliestTimestamp);
  void removeStickyThread(@NonNull ConversationId conversationId);

  class ReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(final Context context, final Intent intent) {
      SignalExecutors.BOUNDED.execute(() -> {
        AppDependencies.getMessageNotifier().updateNotification(context);
      });
    }
  }
}
