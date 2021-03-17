package org.thoughtcrime.securesms.service;

import android.content.Context;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.session.libsession.messaging.threads.Address;
import org.session.libsession.messaging.threads.recipients.Recipient;
import org.session.libsession.utilities.SSKEnvironment;
import org.session.libsignal.libsignal.util.guava.Optional;
import org.session.libsignal.service.api.messages.SignalServiceGroup;
import org.session.libsignal.service.internal.push.SignalServiceProtos;
import org.session.libsignal.service.internal.push.SignalServiceProtos.GroupContext;
import org.session.libsignal.utilities.logging.Log;

import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.session.libsession.messaging.messages.signal.IncomingMediaMessage;
import org.thoughtcrime.securesms.mms.MmsException;

import java.util.Comparator;
import java.util.TreeSet;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class ExpiringMessageManager implements SSKEnvironment.MessageExpirationManagerProtocol {

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

  @Override
  public void setExpirationTimer(@Nullable Long messageID, int duration, @NotNull String senderPublicKey, @NotNull SignalServiceProtos.Content content) {
    try {
      MmsDatabase          database     = DatabaseFactory.getMmsDatabase(context);
      Address              address      = Address.fromSerialized(senderPublicKey);
      Recipient            recipient    = Recipient.from(context, address, false);

      if (recipient.isBlocked()) return;

      Optional<SignalServiceGroup> groupInfo = Optional.absent();
      if (content.getDataMessage().hasGroup()) {
        GroupContext groupContext = content.getDataMessage().getGroup();
        groupInfo = Optional.of(new SignalServiceGroup(groupContext.getId().toByteArray(), SignalServiceGroup.GroupType.SIGNAL));
      }
      IncomingMediaMessage mediaMessage = new IncomingMediaMessage(address, content.getDataMessage().getTimestamp(), -1,
              duration * 1000L, true,
              false,
              Optional.absent(),
              groupInfo,
              Optional.absent(),
              Optional.absent(),
              Optional.absent(),
              Optional.absent());

      database.insertSecureDecryptedMessageInbox(mediaMessage, -1);

      DatabaseFactory.getRecipientDatabase(context).setExpireMessages(recipient, duration);

      if (messageID != null) {
        DatabaseFactory.getSmsDatabase(context).deleteMessage(messageID);
      }
    } catch (MmsException e) {
      Log.e("Loki", "Failed to insert expiration update message.");
    }
  }

  @Override
  public void disableExpirationTimer(@Nullable Long messageID, @NotNull String senderPublicKey, @NotNull SignalServiceProtos.Content content) {
    setExpirationTimer(messageID, 0, senderPublicKey, content);
  }

  @Override
  public void startAnyExpiration(long timestamp, @NotNull String author) {
    MessageRecord messageRecord = DatabaseFactory.getMmsSmsDatabase(context).getMessageFor(timestamp, author);
    if (messageRecord != null) {
      boolean mms = messageRecord.isMms();
      Recipient recipient = messageRecord.getRecipient();
      if (recipient.getExpireMessages() <= 0) return;
      if (mms) {
        mmsDatabase.markExpireStarted(messageRecord.getId());
      } else {
        smsDatabase.markExpireStarted(messageRecord.getId());
      }
      scheduleDeletion(messageRecord.getId(), mms, recipient.getExpireMessages() * 1000);
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
