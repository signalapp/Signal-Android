package org.thoughtcrime.securesms.components;

import android.annotation.SuppressLint;
import android.content.Context;
import android.support.annotation.NonNull;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.jobs.TypingSendJob;
import org.thoughtcrime.securesms.loki.MultiDeviceUtilities;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.signalservice.loki.api.LokiStorageAPI;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import kotlin.Unit;

@SuppressLint("UseSparseArrays")
public class TypingStatusSender {

  private static final String TAG = TypingStatusSender.class.getSimpleName();

  private static final long REFRESH_TYPING_TIMEOUT = TimeUnit.SECONDS.toMillis(10);
  private static final long PAUSE_TYPING_TIMEOUT   = TimeUnit.SECONDS.toMillis(3);

  private final Context              context;
  private final Map<Long, TimerPair> selfTypingTimers;

  public TypingStatusSender(@NonNull Context context) {
    this.context          = context;
    this.selfTypingTimers = new HashMap<>();
  }

  public synchronized void onTypingStarted(long threadId) {
    TimerPair pair = Util.getOrDefault(selfTypingTimers, threadId, new TimerPair());
    selfTypingTimers.put(threadId, pair);

    if (pair.getStart() == null) {
      sendTyping(threadId, true);

      Runnable start = new StartRunnable(threadId);
      Util.runOnMainDelayed(start, REFRESH_TYPING_TIMEOUT);
      pair.setStart(start);
    }

    if (pair.getStop() != null) {
      Util.cancelRunnableOnMain(pair.getStop());
    }

    Runnable stop = () -> onTypingStopped(threadId, true);
    Util.runOnMainDelayed(stop, PAUSE_TYPING_TIMEOUT);
    pair.setStop(stop);
  }

  public synchronized void onTypingStopped(long threadId) {
    onTypingStopped(threadId, false);
  }

  private synchronized void onTypingStopped(long threadId, boolean notify) {
    TimerPair pair = Util.getOrDefault(selfTypingTimers, threadId, new TimerPair());
    selfTypingTimers.put(threadId, pair);

    if (pair.getStart() != null) {
      Util.cancelRunnableOnMain(pair.getStart());

      if (notify) {
        sendTyping(threadId, false);
      }
    }

    if (pair.getStop() != null) {
      Util.cancelRunnableOnMain(pair.getStop());
    }

    pair.setStart(null);
    pair.setStop(null);
  }

  private void sendTyping(long threadId, boolean typingStarted) {
    LokiStorageAPI storageAPI = LokiStorageAPI.Companion.getShared();
    ThreadDatabase threadDatabase = DatabaseFactory.getThreadDatabase(context);
    Recipient recipient = threadDatabase.getRecipientForThreadId(threadId);

    if (recipient == null) {
      ApplicationContext.getInstance(context).getJobManager().add(new TypingSendJob(threadId, typingStarted));
      return;
    }
    LokiStorageAPI.shared.getAllDevicePublicKeys(recipient.getAddress().serialize()).success(devices -> {
      for (String device : devices) {
        Recipient deviceRecipient = Recipient.from(context, Address.fromSerialized(device), false);
        long deviceThreadID = threadDatabase.getThreadIdIfExistsFor(deviceRecipient);
        if (deviceThreadID > -1) {
          ApplicationContext.getInstance(context).getJobManager().add(new TypingSendJob(deviceThreadID, typingStarted));
        }
      }
      return Unit.INSTANCE;
    });
  }

  private class StartRunnable implements Runnable {

    private final long threadId;

    private StartRunnable(long threadId) {
      this.threadId = threadId;
    }

    @Override
    public void run() {
      sendTyping(threadId, true);
      Util.runOnMainDelayed(this, REFRESH_TYPING_TIMEOUT);
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
