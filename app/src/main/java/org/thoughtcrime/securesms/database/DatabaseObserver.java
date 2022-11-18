package org.thoughtcrime.securesms.database;

import android.app.Application;

import androidx.annotation.NonNull;

import org.signal.core.util.concurrent.SignalExecutors;
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

  private final Set<Observer>            conversationListObservers;
  private final Map<Long, Set<Observer>> conversationObservers;
  private final Map<Long, Set<Observer>> verboseConversationObservers;
  private final Map<UUID, Set<Observer>> paymentObservers;
  private final Set<Observer>            allPaymentsObservers;
  private final Set<Observer>            chatColorsObservers;
  private final Set<Observer>            stickerPackObservers;

  public DatabaseObserver(Application application) {
    this.application                  = application;
    this.executor                     = new SerialExecutor(SignalExecutors.BOUNDED);
    this.conversationListObservers    = new HashSet<>();
    this.conversationObservers        = new HashMap<>();
    this.verboseConversationObservers = new HashMap<>();
    this.paymentObservers             = new HashMap<>();
    this.allPaymentsObservers         = new HashSet<>();
    this.chatColorsObservers          = new HashSet<>();
    this.stickerPackObservers         = new HashSet<>();
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

  public void registerStickerPackObserver(@NonNull Observer listener) {
    executor.execute(() -> {
      stickerPackObservers.add(listener);
    });
  }

  public void unregisterObserver(@NonNull Observer listener) {
    executor.execute(() -> {
      conversationListObservers.remove(listener);
      unregisterMapped(conversationObservers, listener);
      unregisterMapped(verboseConversationObservers, listener);
      unregisterMapped(paymentObservers, listener);
      chatColorsObservers.remove(listener);
      stickerPackObservers.remove(listener);
    });
  }

  public void notifyConversationListeners(Set<Long> threadIds) {
    executor.execute(() -> {
      for (long threadId : threadIds) {
        notifyMapped(conversationObservers, threadId);
        notifyMapped(verboseConversationObservers, threadId);
      }

      for (long threadId : threadIds) {
        application.getContentResolver().notifyChange(DatabaseContentProviders.Conversation.getUriForThread(threadId), null);
        application.getContentResolver().notifyChange(DatabaseContentProviders.Conversation.getVerboseUriForThread(threadId), null);
      }
    });
  }

  public void notifyConversationListeners(long threadId) {
    executor.execute(() -> {
      notifyMapped(conversationObservers, threadId);
      notifyMapped(verboseConversationObservers, threadId);

      application.getContentResolver().notifyChange(DatabaseContentProviders.Conversation.getUriForThread(threadId), null);
      application.getContentResolver().notifyChange(DatabaseContentProviders.Conversation.getVerboseUriForThread(threadId), null);
    });
  }

  public void notifyVerboseConversationListeners(Set<Long> threadIds) {
    executor.execute(() -> {
      for (long threadId : threadIds) {
        notifyMapped(verboseConversationObservers, threadId);
      }

      for (long threadId : threadIds) {
        application.getContentResolver().notifyChange(DatabaseContentProviders.Conversation.getVerboseUriForThread(threadId), null);
      }
    });
  }

  public void notifyVerboseConversationListeners(long threadId) {
    executor.execute(() -> {
      notifyMapped(verboseConversationObservers, threadId);

      application.getContentResolver().notifyChange(DatabaseContentProviders.Conversation.getVerboseUriForThread(threadId), null);
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

  public void notifyStickerPackObservers() {
    executor.execute(() -> {
      notifySet(stickerPackObservers);

      application.getContentResolver().notifyChange(DatabaseContentProviders.StickerPack.CONTENT_URI, null);
    });
  }

  private <K> void registerMapped(@NonNull Map<K, Set<Observer>> map, @NonNull K key, @NonNull Observer listener) {
    Set<Observer> listeners = map.get(key);

    if (listeners == null) {
      listeners = new HashSet<>();
    }

    listeners.add(listener);
    map.put(key, listeners);
  }

  private <K> void unregisterMapped(@NonNull Map<K, Set<Observer>> map, @NonNull Observer listener) {
    for (Map.Entry<K, Set<Observer>> entry : map.entrySet()) {
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
}
