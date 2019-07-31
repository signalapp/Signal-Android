package org.thoughtcrime.securesms;

import android.content.Context;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MessagingDatabase.SyncMessageId;
import org.thoughtcrime.securesms.database.MmsSmsDatabase;
import org.thoughtcrime.securesms.database.PushDatabase;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.jobmanager.JobManager;
import org.thoughtcrime.securesms.jobs.DirectoryRefreshJob;
import org.thoughtcrime.securesms.jobs.PushDecryptJob;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;

import java.io.Closeable;
import java.util.Locale;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The central entry point for all envelopes that have been retrieved. Envelopes must be processed
 * here to guarantee proper ordering.
 */
public class IncomingMessageProcessor {

  private static final String TAG = Log.tag(IncomingMessageProcessor.class);

  private final Context       context;
  private final ReentrantLock lock;

  public IncomingMessageProcessor(@NonNull Context context) {
    this.context = context;
    this.lock    = new ReentrantLock();
  }

  /**
   * @return An instance of a Processor that will allow you to process messages in a thread safe
   * way. Must be closed.
   */
  public Processor acquire() {
    lock.lock();

    Thread current = Thread.currentThread();
    Log.d(TAG, "Lock acquired by thread " + current.getId() + " (" + current.getName() + ")");

    return new Processor(context);
  }

  private void release() {
    Thread current = Thread.currentThread();
    Log.d(TAG, "Lock about to be released by thread " + current.getId() + " (" + current.getName() + ")");

    lock.unlock();
  }

  public class Processor implements Closeable {

    private final Context           context;
    private final RecipientDatabase recipientDatabase;
    private final PushDatabase      pushDatabase;
    private final MmsSmsDatabase    mmsSmsDatabase;
    private final JobManager        jobManager;

    private Processor(@NonNull Context context) {
      this.context           = context;
      this.recipientDatabase = DatabaseFactory.getRecipientDatabase(context);
      this.pushDatabase      = DatabaseFactory.getPushDatabase(context);
      this.mmsSmsDatabase    = DatabaseFactory.getMmsSmsDatabase(context);
      this.jobManager        = ApplicationContext.getInstance(context).getJobManager();
    }

    public void processEnvelope(@NonNull SignalServiceEnvelope envelope) {
      if (envelope.hasSource()) {
        Address   source    = Address.fromExternal(context, envelope.getSource());
        Recipient recipient = Recipient.from(context, source, false);

        if (!isActiveNumber(recipient)) {
          recipientDatabase.setRegistered(recipient, RecipientDatabase.RegisteredState.REGISTERED);
          jobManager.add(new DirectoryRefreshJob(recipient, false));
        }
      }

      if (envelope.isReceipt()) {
        processReceipt(envelope);
      } else if (envelope.isPreKeySignalMessage() || envelope.isSignalMessage() || envelope.isUnidentifiedSender()) {
        processMessage(envelope);
      } else {
        Log.w(TAG, "Received envelope of unknown type: " + envelope.getType());
      }
    }

    private void processMessage(@NonNull SignalServiceEnvelope envelope) {
      Log.i(TAG, "Received message. Inserting in PushDatabase.");
      long id = pushDatabase.insert(envelope);
      jobManager.add(new PushDecryptJob(context, id));
    }

    private void processReceipt(@NonNull SignalServiceEnvelope envelope) {
      Log.i(TAG, String.format(Locale.ENGLISH, "Received receipt: (XXXXX, %d)", envelope.getTimestamp()));
      mmsSmsDatabase.incrementDeliveryReceiptCount(new SyncMessageId(Address.fromExternal(context, envelope.getSource()),
              envelope.getTimestamp()),
          System.currentTimeMillis());
    }

    private boolean isActiveNumber(@NonNull Recipient recipient) {
      return recipient.resolve().getRegistered() == RecipientDatabase.RegisteredState.REGISTERED;
    }

    @Override
    public void close() {
      release();
    }
  }
}
