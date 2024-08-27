package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.signal.libsignal.protocol.InvalidMessageException;
import org.signal.libsignal.protocol.message.DecryptionErrorMessage;
import org.thoughtcrime.securesms.crypto.SealedSenderAccessUtil;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.JsonJobData;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.recipients.RecipientUtil;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

public final class SendRetryReceiptJob extends BaseJob {

  private static final String TAG = Log.tag(SendRetryReceiptJob.class);

  public static final String KEY = "SendRetryReceiptJob";

  private static final String KEY_RECIPIENT_ID  = "recipient_id";
  private static final String KEY_ERROR_MESSAGE = "error_message";
  private static final String KEY_GROUP_ID      = "group_id";

  private final RecipientId            recipientId;
  private final Optional<GroupId>      groupId;
  private final DecryptionErrorMessage errorMessage;

  public SendRetryReceiptJob(@NonNull RecipientId recipientId, @NonNull Optional<GroupId> groupId, @NonNull DecryptionErrorMessage errorMessage) {
    this(recipientId,
         groupId,
         errorMessage,
         new Parameters.Builder()
                       .addConstraint(NetworkConstraint.KEY)
                       .setQueue(recipientId.toQueueKey())
                       .setMaxAttempts(Parameters.UNLIMITED)
                       .setLifespan(TimeUnit.DAYS.toMillis(1))
                       .build());
  }

  private SendRetryReceiptJob(@NonNull RecipientId recipientId,
                              @NonNull Optional<GroupId> groupId,
                              @NonNull DecryptionErrorMessage errorMessage,
                              @NonNull Parameters parameters)
  {
    super(parameters);
    this.recipientId  = recipientId;
    this.groupId      = groupId;
    this.errorMessage = errorMessage;
  }

  @Override
  public @Nullable byte[] serialize() {
    JsonJobData.Builder builder = new JsonJobData.Builder()
                                   .putString(KEY_RECIPIENT_ID, recipientId.serialize())
                                   .putBlobAsString(KEY_ERROR_MESSAGE, errorMessage.serialize());

    if (groupId.isPresent()) {
      builder.putBlobAsString(KEY_GROUP_ID, groupId.get().getDecodedId());
    }

    return builder.serialize();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  protected void onRun() throws Exception {
    Recipient recipient = Recipient.resolved(recipientId);

    if (recipient.isUnregistered()) {
      Log.w(TAG, recipient.getId() + " not registered!");
      return;
    }

    SignalServiceAddress address = RecipientUtil.toSignalServiceAddress(context, recipient);
    Optional<byte[]>     group   = groupId.map(GroupId::getDecodedId);

    Log.i(TAG, "Sending retry receipt for " + errorMessage.getTimestamp() + " to " + recipientId + ", device: " + errorMessage.getDeviceId());
    AppDependencies.getSignalServiceMessageSender().sendRetryReceipt(address, SealedSenderAccessUtil.getSealedSenderAccessFor(recipient), group, errorMessage);
  }

  @Override
  protected boolean onShouldRetry(@NonNull Exception e) {
    return e instanceof PushNetworkException;
  }

  @Override
  public void onFailure() {
    AppDependencies.getJobManager().add(new AutomaticSessionResetJob(recipientId, errorMessage.getDeviceId(), System.currentTimeMillis()));
  }

  public static final class Factory implements Job.Factory<SendRetryReceiptJob> {
    @Override
    public @NonNull SendRetryReceiptJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      JsonJobData data = JsonJobData.deserialize(serializedData);

      try {
        RecipientId            recipientId  = RecipientId.from(data.getString(KEY_RECIPIENT_ID));
        DecryptionErrorMessage errorMessage = new DecryptionErrorMessage(data.getStringAsBlob(KEY_ERROR_MESSAGE));
        Optional<GroupId>      groupId      = Optional.empty();

        if (data.hasString(KEY_GROUP_ID)) {
          groupId = Optional.of(GroupId.pushOrThrow(data.getStringAsBlob(KEY_GROUP_ID)));
        }

        return new SendRetryReceiptJob(recipientId, groupId, errorMessage, parameters);
     } catch (InvalidKeyException | InvalidMessageException e) {
        throw new AssertionError(e);
      }
    }
  }
}
