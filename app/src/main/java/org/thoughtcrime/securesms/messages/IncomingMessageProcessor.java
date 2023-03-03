package org.thoughtcrime.securesms.messages;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.GroupTable;
import org.thoughtcrime.securesms.database.MessageTable.SyncMessageId;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.JobManager;
import org.thoughtcrime.securesms.jobs.PushDecryptMessageJob;
import org.thoughtcrime.securesms.jobs.PushProcessMessageJob;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.GroupUtil;
import org.signal.core.util.SetUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.messages.SignalServiceGroupV2;

import java.io.Closeable;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The central entry point for all envelopes that have been retrieved. Envelopes must be processed
 * here to guarantee proper ordering.
 */
public class IncomingMessageProcessor {

  private static final String TAG = Log.tag(IncomingMessageProcessor.class);

  private final Application   context;
  private final ReentrantLock lock;

  public IncomingMessageProcessor(@NonNull Application context) {
    this.context = context;
    this.lock    = new ReentrantLock();
  }

  /**
   * @return An instance of a Processor that will allow you to process messages in a thread safe
   * way. Must be closed.
   */
  public Processor acquire() {
    lock.lock();
    return new Processor(context);
  }

  private void release() {
    lock.unlock();
  }

  public class Processor implements Closeable {

    private final Context     context;
    private final JobManager  jobManager;

    private Processor(@NonNull Context context) {
      this.context           = context;
      this.jobManager        = ApplicationDependencies.getJobManager();
    }

    /**
     * @return The id of the {@link PushDecryptMessageJob} that was scheduled to process the message, if
     *         one was created. Otherwise null.
     */
    public @Nullable String processEnvelope(@NonNull SignalServiceEnvelope envelope) {
      if (envelope.hasSourceUuid()) {
        Recipient.externalPush(envelope.getSourceAddress());
      }

      if (envelope.isReceipt()) {
        processReceipt(envelope);
        return null;
      } else if (envelope.isPreKeySignalMessage() || envelope.isSignalMessage() || envelope.isUnidentifiedSender() || envelope.isPlaintextContent()) {
        return processMessage(envelope);
      } else {
        Log.w(TAG, "Received envelope of unknown type: " + envelope.getType());
        return null;
      }
    }

    private @Nullable String processMessage(@NonNull SignalServiceEnvelope envelope) {
      return processMessageDeferred(envelope);
    }

    private @Nullable String processMessageDeferred(@NonNull SignalServiceEnvelope envelope) {
      Job job = new PushDecryptMessageJob(envelope);
      jobManager.add(job);
      return job.getId();
    }

    private void processReceipt(@NonNull SignalServiceEnvelope envelope) {
      Recipient sender = Recipient.externalPush(envelope.getSourceAddress());
      Log.i(TAG, "Received server receipt. Sender: " + sender.getId() + ", Device: " + envelope.getSourceDevice() + ", Timestamp: " + envelope.getTimestamp());

      SignalDatabase.messages().incrementDeliveryReceiptCount(new SyncMessageId(sender.getId(), envelope.getTimestamp()), System.currentTimeMillis());
      SignalDatabase.messageLog().deleteEntryForRecipient(envelope.getTimestamp(), sender.getId(), envelope.getSourceDevice());
    }

    @Override
    public void close() {
      release();
    }
  }
}
