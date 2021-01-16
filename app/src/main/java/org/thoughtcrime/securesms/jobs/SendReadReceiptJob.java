package org.thoughtcrime.securesms.jobs;


import android.app.Application;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.crypto.UnidentifiedAccessUtil;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.JobManager;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.recipients.RecipientUtil;
import org.thoughtcrime.securesms.transport.UndeliverableMessageException;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.SignalServiceReceiptMessage;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;
import org.whispersystems.signalservice.api.push.exceptions.ServerRejectedException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class SendReadReceiptJob extends BaseJob {

  public static final String KEY = "SendReadReceiptJob";

  private static final String TAG = SendReadReceiptJob.class.getSimpleName();

  static final int MAX_TIMESTAMPS = 500;

  private static final String KEY_THREAD      = "thread";
  private static final String KEY_ADDRESS     = "address";
  private static final String KEY_RECIPIENT   = "recipient";
  private static final String KEY_MESSAGE_IDS = "message_ids";
  private static final String KEY_TIMESTAMP   = "timestamp";

  private final long        threadId;
  private final RecipientId recipientId;
  private final List<Long>  messageIds;
  private final long        timestamp;

  public SendReadReceiptJob(long threadId, @NonNull RecipientId recipientId, List<Long> messageIds) {
    this(new Job.Parameters.Builder()
                           .addConstraint(NetworkConstraint.KEY)
                           .setLifespan(TimeUnit.DAYS.toMillis(1))
                           .setMaxAttempts(Parameters.UNLIMITED)
                           .build(),
         threadId,
         recipientId,
         ensureSize(messageIds, MAX_TIMESTAMPS),
         System.currentTimeMillis());
  }

  private SendReadReceiptJob(@NonNull Job.Parameters parameters,
                             long threadId,
                             @NonNull RecipientId recipientId,
                             @NonNull List<Long> messageIds,
                             long timestamp)
  {
    super(parameters);

    this.threadId    = threadId;
    this.recipientId = recipientId;
    this.messageIds  = messageIds;
    this.timestamp   = timestamp;
  }

  /**
   * Enqueues all the necessary jobs for read receipts, ensuring that they're all within the
   * maximum size.
   */
  public static void enqueue(long threadId, @NonNull RecipientId recipientId, List<Long> messageIds) {
    JobManager       jobManager      = ApplicationDependencies.getJobManager();
    List<List<Long>> messageIdChunks = Util.chunk(messageIds, MAX_TIMESTAMPS);

    if (messageIdChunks.size() > 1) {
      Log.w(TAG, "Large receipt count! Had to break into multiple chunks. Total count: " + messageIds.size());
    }

    for (List<Long> chunk : messageIdChunks) {
      jobManager.add(new SendReadReceiptJob(threadId, recipientId, chunk));
    }
  }

  @Override
  public @NonNull Data serialize() {
    long[] ids = new long[messageIds.size()];
    for (int i = 0; i < ids.length; i++) {
      ids[i] = messageIds.get(i);
    }

    return new Data.Builder().putString(KEY_RECIPIENT, recipientId.serialize())
                             .putLongArray(KEY_MESSAGE_IDS, ids)
                             .putLong(KEY_TIMESTAMP, timestamp)
                             .putLong(KEY_THREAD, threadId)
                             .build();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onRun() throws IOException, UntrustedIdentityException, UndeliverableMessageException {
    if (!TextSecurePreferences.isReadReceiptsEnabled(context) || messageIds.isEmpty()) return;

    if (!RecipientUtil.isMessageRequestAccepted(context, threadId)) {
      Log.w(TAG, "Refusing to send receipts to untrusted recipient");
      return;
    }

    Recipient recipient = Recipient.resolved(recipientId);
    if (recipient.isBlocked()) {
      Log.w(TAG, "Refusing to send receipts to blocked recipient");
      return;
    }

    if (recipient.isGroup()) {
      Log.w(TAG, "Refusing to send receipts to group");
      return;
    }

    SignalServiceMessageSender  messageSender  = ApplicationDependencies.getSignalServiceMessageSender();
    SignalServiceAddress        remoteAddress  = RecipientUtil.toSignalServiceAddress(context, recipient);
    SignalServiceReceiptMessage receiptMessage = new SignalServiceReceiptMessage(SignalServiceReceiptMessage.Type.READ, messageIds, timestamp);

    messageSender.sendReceipt(remoteAddress,
                              UnidentifiedAccessUtil.getAccessFor(context, Recipient.resolved(recipientId)),
                              receiptMessage);
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception e) {
    if (e instanceof ServerRejectedException) return false;
    if (e instanceof PushNetworkException) return true;
    return false;
  }

  @Override
  public void onFailure() {
    Log.w(TAG, "Failed to send read receipts to: " + recipientId);
  }

  static <E> List<E> ensureSize(@NonNull List<E> list, int maxSize) {
    if (list.size() > maxSize) {
      throw new IllegalArgumentException("Too large! Size: " + list.size() + ", maxSize: " + maxSize);
    }
    return list;
  }

  public static final class Factory implements Job.Factory<SendReadReceiptJob> {

    private final Application application;

    public Factory(@NonNull Application application) {
      this.application = application;
    }

    @Override
    public @NonNull SendReadReceiptJob create(@NonNull Parameters parameters, @NonNull Data data) {
      long        timestamp   = data.getLong(KEY_TIMESTAMP);
      long[]      ids         = data.hasLongArray(KEY_MESSAGE_IDS) ? data.getLongArray(KEY_MESSAGE_IDS) : new long[0];
      List<Long>  messageIds  = new ArrayList<>(ids.length);
      RecipientId recipientId = data.hasString(KEY_RECIPIENT) ? RecipientId.from(data.getString(KEY_RECIPIENT))
                                                              : Recipient.external(application, data.getString(KEY_ADDRESS)).getId();
      long        threadId    = data.getLong(KEY_THREAD);

      for (long id : ids) {
        messageIds.add(id);
      }

      return new SendReadReceiptJob(parameters, threadId, recipientId, messageIds, timestamp);
    }
  }
}
