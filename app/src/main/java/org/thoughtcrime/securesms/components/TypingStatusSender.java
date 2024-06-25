package org.thoughtcrime.securesms.components;

import android.annotation.SuppressLint;

import org.signal.core.util.ThreadUtil;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.jobs.TypingSendJob;
import org.thoughtcrime.securesms.util.Util;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@SuppressLint("UseSparseArrays")
public class TypingStatusSender {

  private static final String TAG = Log.tag(TypingStatusSender.class);

  private static final long REFRESH_TYPING_TIMEOUT = TimeUnit.SECONDS.toMillis(10);
  private static final long PAUSE_TYPING_TIMEOUT   = TimeUnit.SECONDS.toMillis(3);

  private final Map<Long, TimerPair> selfTypingTimers;

  public TypingStatusSender() {
    this.selfTypingTimers = new HashMap<>();
  }

  public synchronized void onTypingStarted(long threadId) {
    TimerPair pair = Util.getOrDefault(selfTypingTimers, threadId, new TimerPair());
    selfTypingTimers.put(threadId, pair);

    if (pair.getStart() == null) {
      sendTyping(threadId, true);

      Runnable start = new StartRunnable(threadId);
      ThreadUtil.runOnMainDelayed(start, REFRESH_TYPING_TIMEOUT);
      pair.setStart(start);
    }

    if (pair.getStop() != null) {
      ThreadUtil.cancelRunnableOnMain(pair.getStop());
    }

    Runnable stop = () -> onTypingStopped(threadId, true);
    ThreadUtil.runOnMainDelayed(stop, PAUSE_TYPING_TIMEOUT);
    pair.setStop(stop);
  }

  public synchronized void onTypingStopped(long threadId) {
    onTypingStopped(threadId, false);
  }

  public synchronized void onTypingStoppedWithNotify(long threadId) {
    onTypingStopped(threadId, true);
  }

  private synchronized void onTypingStopped(long threadId, boolean notify) {
    TimerPair pair = Util.getOrDefault(selfTypingTimers, threadId, new TimerPair());
    selfTypingTimers.put(threadId, pair);

    if (pair.getStart() != null) {
      ThreadUtil.cancelRunnableOnMain(pair.getStart());

      if (notify) {
        sendTyping(threadId, false);
      }
    }

    if (pair.getStop() != null) {
      ThreadUtil.cancelRunnableOnMain(pair.getStop());
    }

    pair.setStart(null);
    pair.setStop(null);
  }

  private void sendTyping(long threadId, boolean typingStarted) {
    AppDependencies.getJobManager().add(new TypingSendJob(threadId, typingStarted));
  }

  private class StartRunnable implements Runnable {

    private final long threadId;

    private StartRunnable(long threadId) {
      this.threadId = threadId;
    }

    @Override
    public void run() {
      sendTyping(threadId, true);
      ThreadUtil.runOnMainDelayed(this, REFRESH_TYPING_TIMEOUT);
    }
  }

  private static class TimerPair {
    private Runnable start;
    private Runnable stop;

    public Runnable getStart() {
      return start;
    }

    public void setStart(Runnable start) {
      this.start = start;
    }

    public Runnable getStop() {
      return stop;
    }

    public void setStop(Runnable stop) {
      this.stop = stop;
    }
  }
}
