package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.ThreadUtil;
import org.signal.core.util.logging.Log;
import org.signal.libsignal.protocol.SignalProtocolAddress;
import org.signal.libsignal.protocol.message.SenderKeyDistributionMessage;
import org.thoughtcrime.securesms.crypto.SealedSenderAccessUtil;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.model.DistributionListRecord;
import org.thoughtcrime.securesms.database.model.GroupRecord;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.JsonJobData;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.recipients.RecipientUtil;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.ContentHint;
import org.whispersystems.signalservice.api.crypto.SealedSenderAccess;
import org.whispersystems.signalservice.api.messages.SendMessageResult;
import org.whispersystems.signalservice.api.push.DistributionId;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;
import org.whispersystems.signalservice.internal.push.Content;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import okio.ByteString;

/**
 * Resends a previously-sent message in response to receiving a retry receipt.
 *
 * Not for arbitrary retries due to network flakiness or something -- those should be handled within each individual job.
 */
public class ResendMessageJob extends BaseJob {

  public static final String KEY = "ResendMessageJob";

  private static final String TAG = Log.tag(ResendMessageJob.class);

  private final RecipientId    recipientId;
  private final long           sentTimestamp;
  private final Content        content;
  private final ContentHint    contentHint;
  private final boolean        urgent;
  private final GroupId.V2     groupId;
  private final DistributionId distributionId;

  private static final String KEY_RECIPIENT_ID    = "recipient_id";
  private static final String KEY_SENT_TIMESTAMP  = "sent_timestamp";
  private static final String KEY_CONTENT         = "content";
  private static final String KEY_CONTENT_HINT    = "content_hint";
  private static final String KEY_URGENT          = "urgent";
  private static final String KEY_GROUP_ID        = "group_id";
  private static final String KEY_DISTRIBUTION_ID = "distribution_id";

  public ResendMessageJob(@NonNull RecipientId recipientId,
                          long sentTimestamp,
                          @NonNull Content content,
                          @NonNull ContentHint contentHint,
                          boolean urgent,
                          @Nullable GroupId.V2 groupId,
                          @Nullable DistributionId distributionId)
  {
    this(recipientId,
         sentTimestamp,
         content,
         contentHint,
         urgent,
         groupId,
         distributionId,
         new Parameters.Builder().setQueue(recipientId.toQueueKey())
                                 .setLifespan(TimeUnit.DAYS.toMillis(1))
                                 .setMaxAttempts(Parameters.UNLIMITED)
                                 .addConstraint(NetworkConstraint.KEY)
                                 .build());
  }

  private ResendMessageJob(@NonNull RecipientId recipientId,
                           long sentTimestamp,
                           @NonNull Content content,
                           @NonNull ContentHint contentHint,
                           boolean urgent,
                           @Nullable GroupId.V2 groupId,
                           @Nullable DistributionId distributionId,
                           @NonNull Parameters parameters)
  {
    super(parameters);

    this.recipientId    = recipientId;
    this.sentTimestamp  = sentTimestamp;
    this.content        = content;
    this.contentHint    = contentHint;
    this.urgent         = urgent;
    this.groupId        = groupId;
    this.distributionId = distributionId;
  }

  @Override
  public @Nullable byte[] serialize() {
    return new JsonJobData.Builder()
                   .putString(KEY_RECIPIENT_ID, recipientId.serialize())
                   .putLong(KEY_SENT_TIMESTAMP, sentTimestamp)
                   .putBlobAsString(KEY_CONTENT, content.encode())
                   .putInt(KEY_CONTENT_HINT, contentHint.getType())
                   .putBoolean(KEY_URGENT, urgent)
                   .putBlobAsString(KEY_GROUP_ID, groupId != null ? groupId.getDecodedId() : null)
                   .putString(KEY_DISTRIBUTION_ID, distributionId != null ? distributionId.toString() : null)
                   .serialize();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  protected void onRun() throws Exception {
    if (SignalStore.internal().delayResends()) {
      Log.w(TAG, "Delaying resend by 10 sec because of an internal preference.");
      ThreadUtil.sleep(10000);
    }

    Log.i(TAG, "[" + sentTimestamp + " ] Resending message to " + recipientId + " (urgent: " + urgent + ", contentHint: " + contentHint.name() + ", groupId: " + groupId + ", distributionId: " + distributionId + ")");

    SignalServiceMessageSender messageSender = AppDependencies.getSignalServiceMessageSender();
    Recipient                  recipient     = Recipient.resolved(recipientId);

    if (recipient.isUnregistered()) {
      Log.w(TAG, recipient.getId() + " is unregistered!");
      return;
    }

    SignalServiceAddress                    address              = RecipientUtil.toSignalServiceAddress(context, recipient);
    Content                                 contentToSend        = content;
    SealedSenderAccess.CreateGroupSendToken createGroupSendToken = null;

    if (distributionId != null) {
      if (groupId != null) {
        Log.d(TAG, "GroupId is present. Assuming this is a group message.");
        Optional<GroupRecord> groupRecord = SignalDatabase.groups().getGroupByDistributionId(distributionId);

        if (!groupRecord.isPresent()) {
          Log.w(TAG, "Could not find a matching group for the distributionId! Skipping message send.");
          return;
        } else if (!groupRecord.get().getMembers().contains(recipientId)) {
          Log.w(TAG, "The target user is no longer in the group! Skipping message send.");
          return;
        }

        createGroupSendToken = () -> SignalDatabase.groups().getGroupSendFullToken(groupId, recipientId);
      } else {
        Log.d(TAG, "GroupId is not present. Assuming this is a message for a distribution list.");
        DistributionListRecord listRecord = SignalDatabase.distributionLists().getListByDistributionId(distributionId);

        if (listRecord == null) {
          Log.w(TAG, "Could not find a matching distribution list for the distributionId! Skipping message send.");
          return;
        } else if (!listRecord.getMembers().contains(recipientId)) {
          Log.w(TAG, "The target user is no longer in the distribution list! Skipping message send.");
          return;
        }
      }

      SenderKeyDistributionMessage senderKeyDistributionMessage = messageSender.getOrCreateNewGroupSession(distributionId);
      ByteString                   distributionBytes            = ByteString.of(senderKeyDistributionMessage.serialize());

      contentToSend = contentToSend.newBuilder().senderKeyDistributionMessage(distributionBytes).build();
    }

    SendMessageResult result;

    SealedSenderAccess access = SealedSenderAccessUtil.getSealedSenderAccessFor(recipient, createGroupSendToken);

    try {
      result = messageSender.resendContent(address, access, sentTimestamp, contentToSend, contentHint, Optional.ofNullable(groupId).map(GroupId::getDecodedId), urgent);
    } catch (IllegalStateException e) {
      Log.w(TAG, "Failed to resend content. Archiving session and trying again.", e);
      AppDependencies.getProtocolStore().aci().sessions().archiveSessions(recipientId, SignalServiceAddress.DEFAULT_DEVICE_ID);
      AppDependencies.getProtocolStore().aci().sessions().archiveSiblingSessions(recipient.requireServiceId().toProtocolAddress(SignalServiceAddress.DEFAULT_DEVICE_ID));
      AppDependencies.getProtocolStore().pni().sessions().archiveSessions(recipientId, SignalServiceAddress.DEFAULT_DEVICE_ID);
      AppDependencies.getProtocolStore().pni().sessions().archiveSiblingSessions(recipient.requireServiceId().toProtocolAddress(SignalServiceAddress.DEFAULT_DEVICE_ID));
      SignalDatabase.senderKeyShared().deleteAllFor(recipientId);

      result = messageSender.resendContent(address, access, sentTimestamp, contentToSend, contentHint, Optional.ofNullable(groupId).map(GroupId::getDecodedId), urgent);
    }

    if (result.isSuccess() && distributionId != null) {
      List<SignalProtocolAddress> addresses = result.getSuccess()
                                                    .getDevices()
                                                    .stream()
                                                    .map(device -> recipient.requireServiceId().toProtocolAddress(device))
                                                    .collect(Collectors.toList());

      AppDependencies.getProtocolStore().aci().markSenderKeySharedWith(distributionId, addresses);
    }
  }

  @Override
  protected boolean onShouldRetry(@NonNull Exception e) {
    return e instanceof PushNetworkException;
  }

  @Override
  public void onFailure() {
  }

  public static final class Factory implements Job.Factory<ResendMessageJob> {

    @Override
    public @NonNull ResendMessageJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      JsonJobData data = JsonJobData.deserialize(serializedData);

      Content content;
      try {
        content = Content.ADAPTER.decode(data.getStringAsBlob(KEY_CONTENT));
      } catch (IOException e) {
        throw new AssertionError(e);
      }

      byte[]     rawGroupId = data.getStringAsBlob(KEY_GROUP_ID);
      GroupId.V2 groupId    = rawGroupId != null ? GroupId.pushOrThrow(rawGroupId).requireV2() : null;

      String         rawDistributionId = data.getString(KEY_DISTRIBUTION_ID);
      DistributionId distributionId    = rawDistributionId != null ? DistributionId.from(rawDistributionId) : null;

      return new ResendMessageJob(RecipientId.from(data.getString(KEY_RECIPIENT_ID)),
                                  data.getLong(KEY_SENT_TIMESTAMP),
                                  content,
                                  ContentHint.fromType(data.getInt(KEY_CONTENT_HINT)),
                                  data.getBooleanOrDefault(KEY_URGENT, true),
                                  groupId,
                                  distributionId,
                                  parameters);
    }
  }
}
