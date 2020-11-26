package org.thoughtcrime.securesms.service;

import android.content.Context;
import org.thoughtcrime.securesms.logging.Log;

import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.database.model.MessageRecord;

import java.util.Comparator;
import java.util.TreeSet;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class ExpiringMessageManager {

  private static final String TAG = ExpiringMessageManager.class.getSimpleName();

  private final TreeSet<ExpiringMessageReference> expiringMessageReferences = new TreeSet<>(new ExpiringMessageComparator());
  private final Executor                          executor                  = Executors.newSingleThreadExecutor();

  private final SmsDatabase smsDatabase;
  private final MmsDatabase mmsDatabase;
  private final Context     context;

  public ExpiringMessageManager(Context context) {
    this.context     = context.getApplicationContext();
    this.smsDatabase = DatabaseFactory.getSmsDatabase(context);
    this.mmsDatabase = DatabaseFactory.getMmsDatabase(context);

    executor.execute(new LoadTask());
    executor.execute(new ProcessTask());
  }

  public void scheduleDeletion(long id, boolean mms, long expiresInMillis) {
    scheduleDeletion(id, mms, System.currentTimeMillis(), expiresInMillis);
  }

  public void scheduleDeletion(long id, boolean mms, long startedAtTimestamp, long expiresInMillis) {
    long expiresAtMillis = startedAtTimestamp + expiresInMillis;

    synchronized (expiringMessageReferences) {
      expiringMessageReferences.add(new ExpiringMessageReference(id, mms, expiresAtMillis));
      expiringMessageReferences.notifyAll();
    }
  }

  public void checkSchedule() {
    synchronized (expiringMessageReferences) {
      expiringMessageReferences.notifyAll();
    }
  }

  private class LoadTask implements Runnable {
    public void run() {
      SmsDatabase.Reader smsReader = smsDatabase.readerFor(smsDatabase.getExpirationStartedMessages());
      MmsDatabase.Reader mmsReader = mmsDatabase.getExpireStartedMessages();

      MessageRecord messageRecord;

      while ((messageRecord = smsReader.getNext()) != null) {
        expiringMessageReferences.add(new ExpiringMessageReference(messageRecord.getId(),
                                                                   messageRecord.isMms(),
                                                                   messageRecord.getExpireStarted() + messageRecord.getExpiresIn()));
      }

      while ((messageRecord = mmsReader.getNext()) != null) {
        expiringMessageReferences.add(new ExpiringMessageReference(messageRecord.getId(),
                                                                   messageRecord.isMms(),
                                                                   messageRecord.getExpireStarted() + messageRecord.getExpiresIn()));
      }

      smsReader.close();
      mmsReader.close();
    }
  }

  @SuppressWarnings("InfiniteLoopStatement")
  private class ProcessTask implements Runnable {
    public void run() {
      while (true) {
        ExpiringMessageReference expiredMessage = null;

        synchronized (expiringMessageReferences) {
          try {
            while (expiringMessageReferences.isEmpty()) expiringMessageReferences.wait();

            ExpiringMessageReference nextReference = expiringMessageReferences.first();
            long                     waitTime      = nextReference.expiresAtMillis - System.currentTimeMillis();

            if (waitTime > 0) {
              ExpirationListener.setAlarm(context, waitTime);
              expiringMessageReferences.wait(waitTime);
            } else {
              expiredMessage = nextReference;
              expiringMessageReferences.remove(nextReference);
            }

          } catch (InterruptedException e) {
            Log.w(TAG, e);
          }
        }

        if (expiredMessage != null) {
          if (expiredMessage.mms) mmsDatabase.delete(expiredMessage.id);
          else                    smsDatabase.deleteMessage(expiredMessage.id);
        }
      }
    }
  }

  private static class ExpiringMessageReference {
    private final long    id;
    private final boolean mms;
    private final long    expiresAtMillis;

    private ExpiringMessageReference(long id, boolean mms, long expiresAtMillis) {
      this.id = id;
      this.mms = mms;
      this.expiresAtMillis = expiresAtMillis;
    }

    @Override
    public boolean equals(Object other) {
      if (other == null) return false;
      if (!(other instanceof ExpiringMessageReference)) return false;

      ExpiringMessageReference that = (ExpiringMessageReference)other;
      return this.id == that.id && this.mms == that.mms && this.expiresAtMillis == that.expiresAtMillis;
    }

    @Override
    public int hashCode() {
      return (int)this.id ^ (mms ? 1 : 0) ^ (int)expiresAtMillis;
    }
  }

  private static class ExpiringMessageComparator implements Comparator<ExpiringMessageReference> {
    @Override
    public int compare(ExpiringMessageReference lhs, ExpiringMessageReference rhs) {
      if      (lhs.expiresAtMillis < rhs.expiresAtMillis) return -1;
      else if (lhs.expiresAtMillis > rhs.expiresAtMillis) return 1;
      else if (lhs.id < rhs.id)                           return -1;
      else if (lhs.id > rhs.id)                           return 1;
      else if (!lhs.mms && rhs.mms)                       return -1;
      else if (lhs.mms && !rhs.mms)                       return 1;
      else                                                return 0;
    }
  }

}
