package org.thoughtcrime.securesms.database;

import android.app.Application;

import androidx.annotation.NonNull;

import org.signal.core.util.concurrent.SignalExecutors;
import org.thoughtcrime.securesms.database.model.MessageId;
import org.thoughtcrime.securesms.util.concurrent.SerialExecutor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Allows listening to database changes to varying degrees of specificity.
 *
 * A replacement for the observer system in {@link Database}. We should move to this over time.
 */
public final class DatabaseObserver {

  private final Application application;
  private final Executor    executor;

  private final Set<Observer>                   conversationListObservers;
  private final Map<Long, Set<Observer>>        conversationObservers;
  private final Map<Long, Set<Observer>>        verboseConversationObservers;
  private final Map<UUID, Set<Observer>>        paymentObservers;
  private final Set<Observer>                   allPaymentsObservers;
  private final Set<Observer>                   chatColorsObservers;
  private final Set<Observer>                   stickerObservers;
  private final Set<Observer>                   stickerPackObservers;
  private final Set<Observer>                   attachmentObservers;
  private final Set<MessageObserver>            messageUpdateObservers;
  private final Map<Long, Set<MessageObserver>> messageInsertObservers;

  public DatabaseObserver(Application application) {
    this.application                  = application;
    this.executor                     = new SerialExecutor(SignalExecutors.BOUNDED);
    this.conversationListObservers    = new HashSet<>();
    this.conversationObservers        = new HashMap<>();
    this.verboseConversationObservers = new HashMap<>();
    this.paymentObservers             = new HashMap<>();
    this.allPaymentsObservers         = new HashSet<>();
    this.chatColorsObservers          = new HashSet<>();
    this.stickerObservers             = new HashSet<>();
    this.stickerPackObservers         = new HashSet<>();
    this.attachmentObservers          = new HashSet<>();
    this.messageUpdateObservers       = new HashSet<>();
    this.messageInsertObservers       = new HashMap<>();
  }

  public void registerConversationListObserver(@NonNull Observer listener) {
    executor.execute(() -> {
      conversationListObservers.add(listener);
    });
  }

  public void registerConversationObserver(long threadId, @NonNull Observer listener) {
    executor.execute(() -> {
      registerMapped(conversationObservers, threadId, listener);
    });
  }

  public void registerVerboseConversationObserver(long threadId, @NonNull Observer listener) {
    executor.execute(() -> {
      registerMapped(verboseConversationObservers, threadId, listener);
    });
  }

  public void registerPaymentObserver(@NonNull UUID paymentId, @NonNull Observer listener) {
    executor.execute(() -> {
      registerMapped(paymentObservers, paymentId, listener);
    });
  }

  public void registerAllPaymentsObserver(@NonNull Observer listener) {
    executor.execute(() -> {
      allPaymentsObservers.add(listener);
    });
  }

  public void registerChatColorsObserver(@NonNull Observer listener) {
    executor.execute(() -> {
      chatColorsObservers.add(listener);
    });
  }

  public void registerStickerObserver(@NonNull Observer listener) {
    executor.execute(() -> {
      stickerObservers.add(listener);
    });
  }

  public void registerStickerPackObserver(@NonNull Observer listener) {
    executor.execute(() -> {
      stickerPackObservers.add(listener);
    });
  }

  public void registerAttachmentObserver(@NonNull Observer listener) {
    executor.execute(() -> {
      attachmentObservers.add(listener);
    });
  }

  public void registerMessageUpdateObserver(@NonNull MessageObserver listener) {
    executor.execute(() -> {
      messageUpdateObservers.add(listener);
    });
  }

  public void registerMessageInsertObserver(long threadId, @NonNull MessageObserver listener) {
    executor.execute(() -> {
      registerMapped(messageInsertObservers, threadId, listener);
    });
  }

  public void unregisterObserver(@NonNull Observer listener) {
    executor.execute(() -> {
      conversationListObservers.remove(listener);
      unregisterMapped(conversationObservers, listener);
      unregisterMapped(verboseConversationObservers, listener);
      unregisterMapped(paymentObservers, listener);
      chatColorsObservers.remove(listener);
      stickerObservers.remove(listener);
      stickerPackObservers.remove(listener);
      attachmentObservers.remove(listener);
    });
  }

  public void unregisterObserver(@NonNull MessageObserver listener) {
    executor.execute(() -> {
      messageUpdateObservers.remove(listener);
      unregisterMapped(messageInsertObservers, listener);
    });
  }

  public void notifyConversationListeners(Set<Long> threadIds) {
    executor.execute(() -> {
      for (long threadId : threadIds) {
        notifyMapped(conversationObservers, threadId);
        notifyMapped(verboseConversationObservers, threadId);
      }
    });
  }

  public void notifyConversationListeners(long threadId) {
    executor.execute(() -> {
      notifyMapped(conversationObservers, threadId);
      notifyMapped(verboseConversationObservers, threadId);
    });
  }

  public void notifyVerboseConversationListeners(Set<Long> threadIds) {
    executor.execute(() -> {
      for (long threadId : threadIds) {
        notifyMapped(verboseConversationObservers, threadId);
      }
    });
  }

  public void notifyVerboseConversationListeners(long threadId) {
    executor.execute(() -> {
      notifyMapped(verboseConversationObservers, threadId);
    });
  }

  public void notifyConversationListListeners() {
    executor.execute(() -> {
      for (Observer listener : conversationListObservers) {
        listener.onChanged();
      }
    });
  }

  public void notifyPaymentListeners(@NonNull UUID paymentId) {
    executor.execute(() -> {
      notifyMapped(paymentObservers, paymentId);
    });
  }

  public void notifyAllPaymentsListeners() {
    executor.execute(() -> {
      notifySet(allPaymentsObservers);
    });
  }

  public void notifyChatColorsListeners() {
    executor.execute(() -> {
      for (Observer chatColorsObserver : chatColorsObservers) {
        chatColorsObserver.onChanged();
      }
    });
  }

  public void notifyStickerObservers() {
    executor.execute(() -> {
      notifySet(stickerObservers);
    });
  }

  public void notifyStickerPackObservers() {
    executor.execute(() -> {
      notifySet(stickerPackObservers);
    });
  }

  public void notifyAttachmentObservers() {
    executor.execute(() -> {
      notifySet(attachmentObservers);
    });
  }

  public void notifyMessageUpdateObservers(@NonNull MessageId messageId) {
    executor.execute(() -> {
      messageUpdateObservers.stream().forEach(l -> l.onMessageChanged(messageId));
    });
  }

  public void notifyMessageInsertObservers(long threadId, @NonNull MessageId messageId) {
    executor.execute(() -> {
      Set<MessageObserver> listeners = messageInsertObservers.get(threadId);

      if (listeners != null) {
        listeners.stream().forEach(l -> l.onMessageChanged(messageId));
      }
    });
  }

  private <K, V> void registerMapped(@NonNull Map<K, Set<V>> map, @NonNull K key, @NonNull V listener) {
    Set<V> listeners = map.get(key);

    if (listeners == null) {
      listeners = new HashSet<>();
    }

    listeners.add(listener);
    map.put(key, listeners);
  }

  private <K, V> void unregisterMapped(@NonNull Map<K, Set<V>> map, @NonNull V listener) {
    for (Map.Entry<K, Set<V>> entry : map.entrySet()) {
      entry.getValue().remove(listener);
    }
  }

  private static <K> void notifyMapped(@NonNull Map<K, Set<Observer>> map, @NonNull K key) {
    Set<Observer> listeners = map.get(key);

    if (listeners != null) {
      for (Observer listener : listeners) {
        listener.onChanged();
      }
    }
  }

  public static void notifySet(@NonNull Set<Observer> set) {
    for (final Observer observer : set) {
      observer.onChanged();
    }
  }

  public interface Observer {
    /**
     * Called when the relevant data changes. Executed on a serial executor, so don't do any
     * long-running tasks!
     */
    void onChanged();
  }

  public interface MessageObserver {
    void onMessageChanged(@NonNull MessageId messageId);
  }
}
