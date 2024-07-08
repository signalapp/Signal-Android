package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.signal.libsignal.protocol.SignalProtocolAddress;
import org.signal.libsignal.protocol.message.SenderKeyDistributionMessage;
import org.thoughtcrime.securesms.crypto.SealedSenderAccessUtil;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.model.DistributionListRecord;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.JsonJobData;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.recipients.RecipientUtil;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.SealedSenderAccess;
import org.whispersystems.signalservice.api.messages.SendMessageResult;
import org.whispersystems.signalservice.api.push.DistributionId;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Sends a {@link SenderKeyDistributionMessage} to a target recipient.
 *
 * Will re-check group membership at send time and send the proper distribution message if they're still a member.
 */
public final class SenderKeyDistributionSendJob extends BaseJob {

  private static final String TAG = Log.tag(SenderKeyDistributionSendJob.class);

  public static final String KEY = "SenderKeyDistributionSendJob";

  private static final String KEY_TARGET_RECIPIENT_ID = "recipient_id";
  private static final String KEY_THREAD_RECIPIENT_ID = "thread_recipient_id";

  private final RecipientId targetRecipientId;
  private final RecipientId threadRecipientId;

  public SenderKeyDistributionSendJob(@NonNull RecipientId targetRecipientId, RecipientId threadRecipientId) {
    this(targetRecipientId, threadRecipientId, new Parameters.Builder()
                                                             .setQueue(targetRecipientId.toQueueKey())
                                                             .addConstraint(NetworkConstraint.KEY)
                                                             .setLifespan(TimeUnit.DAYS.toMillis(1))
                                                             .setMaxAttempts(Parameters.UNLIMITED)
                                                             .setMaxInstancesForQueue(1)
                                                             .build());
  }

  private SenderKeyDistributionSendJob(@NonNull RecipientId targetRecipientId, @NonNull RecipientId threadRecipientId, @NonNull Parameters parameters) {
    super(parameters);

    this.targetRecipientId = targetRecipientId;
    this.threadRecipientId = threadRecipientId;
  }

  @Override
  public @Nullable byte[] serialize() {
    return new JsonJobData.Builder().putString(KEY_TARGET_RECIPIENT_ID, targetRecipientId.serialize())
                                    .putString(KEY_THREAD_RECIPIENT_ID, threadRecipientId.serialize())
                                    .serialize();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  protected void onRun() throws Exception {
    Recipient targetRecipient = Recipient.resolved(targetRecipientId);
    Recipient threadRecipient = Recipient.resolved(threadRecipientId);

    if (targetRecipient.isUnregistered()) {
      Log.w(TAG, threadRecipient.getId() + " not registered!");
      return;
    }

    GroupId.V2                              groupId;
    DistributionId                          distributionId;
    SealedSenderAccess.CreateGroupSendToken createGroupSendFullToken = null;

    if (threadRecipient.isPushV2Group()) {
      groupId                  = threadRecipient.requireGroupId().requireV2();
      distributionId           = SignalDatabase.groups().getOrCreateDistributionId(groupId);
      createGroupSendFullToken = () -> SignalDatabase.groups().getGroupSendFullToken(groupId, targetRecipientId);
    } else if (threadRecipient.isDistributionList()) {
      groupId        = null;
      distributionId = SignalDatabase.distributionLists().getDistributionId(threadRecipientId);
    } else {
      warn(TAG, "Recipient is not a group or distribution list! Skipping.");
      return;
    }

    if (distributionId == null) {
      warn(TAG, "Failed to find a distributionId! Skipping.");
      return;
    }

    if (groupId != null && !SignalDatabase.groups().isCurrentMember(groupId, targetRecipientId)) {
      Log.w(TAG, targetRecipientId + " is no longer a member of " + groupId + "! Not sending.");
      return;
    } else if (groupId == null) {
      DistributionListRecord listRecord = SignalDatabase.distributionLists().getList(threadRecipientId);

      if (listRecord == null || !listRecord.getMembers().contains(targetRecipientId)) {
        Log.w(TAG, targetRecipientId + " is no longer a member of the distribution list! Not sending.");
        return;
      }
    }

    SignalServiceMessageSender   messageSender = AppDependencies.getSignalServiceMessageSender();
    List<SignalServiceAddress>   address       = Collections.singletonList(RecipientUtil.toSignalServiceAddress(context, targetRecipient));
    SenderKeyDistributionMessage message       = messageSender.getOrCreateNewGroupSession(distributionId);
    List<SealedSenderAccess>     access        = Collections.singletonList(SealedSenderAccessUtil.getSealedSenderAccessFor(targetRecipient, createGroupSendFullToken));

    SendMessageResult result = messageSender.sendSenderKeyDistributionMessage(distributionId, address, access, message, Optional.ofNullable(groupId).map(GroupId::getDecodedId), false, false).get(0);

    if (result.isSuccess()) {
      List<SignalProtocolAddress> addresses = result.getSuccess()
                                                    .getDevices()
                                                    .stream()
                                                    .map(device -> targetRecipient.requireServiceId().toProtocolAddress(device))
                                                    .collect(Collectors.toList());

      AppDependencies.getProtocolStore().aci().markSenderKeySharedWith(distributionId, addresses);
    }
  }

  @Override
  protected boolean onShouldRetry(@NonNull Exception e) {
    return false;
  }

  @Override
  public void onFailure() {

  }

  public static final class Factory implements Job.Factory<SenderKeyDistributionSendJob> {

    @Override
    public @NonNull SenderKeyDistributionSendJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      JsonJobData data = JsonJobData.deserialize(serializedData);

      return new SenderKeyDistributionSendJob(RecipientId.from(data.getString(KEY_TARGET_RECIPIENT_ID)),
                                              RecipientId.from(data.getString(KEY_THREAD_RECIPIENT_ID)),
                                              parameters);
    }
  }
}
