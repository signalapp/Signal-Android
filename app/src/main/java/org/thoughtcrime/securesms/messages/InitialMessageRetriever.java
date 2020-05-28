package org.thoughtcrime.securesms.messages;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.util.concurrent.SignalExecutors;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Fetches the first batch of messages, before anything else does.
 *
 * We have a separate process for fetching "initial" messages in order to have special behavior when
 * catching up on a lot of messages after being offline for a while. It also gives us an opportunity
 * to flag when we are "up-to-date" with our message queue.
 */
public class InitialMessageRetriever {

  private static final String TAG = Log.tag(InitialMessageRetriever.class);

  private static final int MAX_ATTEMPTS = 3;

  private final List<Listener> listeners = new CopyOnWriteArrayList<>();

  private State state = State.NOT_CAUGHT_UP;

  private final Object STATE_LOCK = new Object();

  /**
   * Only fires once. No need to remove. It will be called on an arbitrary worker thread.
   */
  public void addListener(@NonNull Listener listener) {
    synchronized (STATE_LOCK) {
      if (state == State.CAUGHT_UP) {
        listener.onCaughtUp();
      } else {
        listeners.add(listener);
      }
    }
  }

  /**
   * Performs the initial fetch for messages (if necessary) with the requested timeout. The timeout
   * is not just for the initial network request, but for the entire method call.
   *
   * @return A result describing how the operation completed.
   */
  @WorkerThread
  public @NonNull Result begin(long timeout) {
    synchronized (STATE_LOCK) {
      if (state == State.CAUGHT_UP) {
        return Result.SKIPPED_ALREADY_CAUGHT_UP;
      } else if (state == State.RUNNING) {
        return Result.SKIPPED_ALREADY_RUNNING;
      }

      state = State.RUNNING;
    }

    long startTime = System.currentTimeMillis();

    MessageRetrievalStrategy messageRetrievalStrategy = getRetriever();
    CountDownLatch           latch                    = new CountDownLatch(1);

    SignalExecutors.UNBOUNDED.execute(() -> {
      for (int i = 0; i < MAX_ATTEMPTS; i++) {
        if (messageRetrievalStrategy.isCanceled()) {
          Log.w(TAG, "Invalidated! Ending attempts.");
          break;
        }

        boolean success = getRetriever().execute(timeout);

        if (success) {
          break;
        } else {
          Log.w(TAG, "Failed to catch up! Attempt " + (i + 1) + "/" + MAX_ATTEMPTS);
        }
      }

      latch.countDown();
    });

    try {
      boolean success = latch.await(timeout, TimeUnit.MILLISECONDS);

      synchronized (STATE_LOCK) {
        state = State.CAUGHT_UP;

        for (Listener listener : listeners) {
          listener.onCaughtUp();
        }

        listeners.clear();
      }

      ApplicationDependencies.getMessageNotifier().updateNotification(ApplicationDependencies.getApplication());

      if (success) {
        Log.i(TAG, "Successfully caught up in " + (System.currentTimeMillis() - startTime) + " ms.");
        return Result.SUCCESS;
      } else {
        Log.i(TAG, "Could not catch up completely. Hit the timeout of " + timeout + " ms.");
        messageRetrievalStrategy.cancel();
        return Result.FAILURE_TIMEOUT;
      }
    } catch (InterruptedException e) {
      Log.w(TAG, "Interrupted!", e);
      return Result.FAILURE_ERROR;
    }
  }

  public boolean isCaughtUp() {
    synchronized (STATE_LOCK) {
      return state == State.CAUGHT_UP;
    }
  }

  private @NonNull MessageRetrievalStrategy getRetriever() {
    Context context = ApplicationDependencies.getApplication();

    if (ApplicationContext.getInstance(context).isAppVisible() &&
        !ApplicationDependencies.getSignalServiceNetworkAccess().isCensored(context))
    {
      return new WebsocketStrategy();
    } else {
      return new RestStrategy();
    }
  }

  private enum State {
    NOT_CAUGHT_UP, RUNNING, CAUGHT_UP
  }

  public enum Result {
    SUCCESS, FAILURE_TIMEOUT, FAILURE_ERROR, SKIPPED_ALREADY_CAUGHT_UP, SKIPPED_ALREADY_RUNNING
  }

  public interface Listener {
    @WorkerThread
    void onCaughtUp();
  }
}
