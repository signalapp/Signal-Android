package org.thoughtcrime.securesms.jobs;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;

import org.signal.core.util.logging.Log;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.thoughtcrime.securesms.database.RecipientTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.JsonJobData;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.messages.GroupSendUtil;
import org.thoughtcrime.securesms.mms.MessageGroupContext;
import org.thoughtcrime.securesms.mms.OutgoingMessage;
import org.thoughtcrime.securesms.net.NotPushRegisteredException;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.transport.RetryLaterException;
import org.signal.core.util.Base64;
import org.thoughtcrime.securesms.util.GroupUtil;
import org.whispersystems.signalservice.api.crypto.ContentHint;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.groupsv2.DecryptedGroupUtil;
import org.whispersystems.signalservice.api.messages.SendMessageResult;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceGroupV2;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.push.ServiceId.ACI;
import org.whispersystems.signalservice.api.push.exceptions.ServerRejectedException;
import org.whispersystems.signalservice.internal.push.GroupContextV2;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Sends an update to a group without inserting a change message locally.
 * <p>
 * An example usage would be to update a group with a profile key change.
 */
public final class PushGroupSilentUpdateSendJob extends BaseJob {

  public static final String KEY = "PushGroupSilentSendJob";

  private static final String TAG = Log.tag(PushGroupSilentUpdateSendJob.class);

  private static final String KEY_RECIPIENTS              = "recipients";
  private static final String KEY_INITIAL_RECIPIENT_COUNT = "initial_recipient_count";
  private static final String KEY_TIMESTAMP               = "timestamp";
  private static final String KEY_GROUP_CONTEXT_V2        = "group_context_v2";

  private final List<RecipientId> recipients;
  private final int               initialRecipientCount;
  private final GroupContextV2    groupContextV2;
  private final long              timestamp;

  @WorkerThread
  public static @NonNull Job create(@NonNull Context context,
                                    @NonNull GroupId.V2 groupId,
                                    @NonNull DecryptedGroup decryptedGroup,
                                    @NonNull OutgoingMessage groupMessage)
  {
    List<ACI>       memberAcis        = DecryptedGroupUtil.toAciList(decryptedGroup.members);
    List<ServiceId> pendingServiceIds = DecryptedGroupUtil.pendingToServiceIdList(decryptedGroup.pendingMembers);

    Stream<ACI>       memberServiceIds          = Stream.of(memberAcis)
                                                        .filter(ACI::isValid)
                                                        .filter(aci -> !SignalStore.account().requireAci().equals(aci));
    Stream<ServiceId> filteredPendingServiceIds = Stream.of(pendingServiceIds)
                                                        .filterNot(ServiceId::isUnknown);

    Set<RecipientId> recipients = Stream.concat(memberServiceIds, filteredPendingServiceIds)
                                        .map(serviceId -> Recipient.externalPush(serviceId))
                                        .filter(recipient -> recipient.getRegistered() != RecipientTable.RegisteredState.NOT_REGISTERED)
                                        .map(Recipient::getId)
                                        .collect(Collectors.toSet());

    MessageGroupContext.GroupV2Properties properties   = groupMessage.requireGroupV2Properties();
    GroupContextV2                        groupContext = properties.getGroupContext();

    String queue = Recipient.externalGroupExact(groupId).getId().toQueueKey();

    return new PushGroupSilentUpdateSendJob(new ArrayList<>(recipients),
                                            recipients.size(),
                                            groupMessage.getSentTimeMillis(),
                                            groupContext,
                                            new Parameters.Builder()
                                                          .setQueue(queue)
                                                          .setLifespan(TimeUnit.DAYS.toMillis(1))
                                                          .setMaxAttempts(Parameters.UNLIMITED)
                                                          .setGlobalPriority(Parameters.PRIORITY_LOW)
                                                          .build());
  }

  private PushGroupSilentUpdateSendJob(@NonNull List<RecipientId> recipients,
                                       int initialRecipientCount,
                                       long timestamp,
                                       @NonNull GroupContextV2 groupContextV2,
                                       @NonNull Parameters parameters)
  {
    super(parameters);

    this.recipients            = recipients;
    this.initialRecipientCount = initialRecipientCount;
    this.groupContextV2        = groupContextV2;
    this.timestamp             = timestamp;
  }

  @Override
  public @Nullable byte[] serialize() {
    return new JsonJobData.Builder().putString(KEY_RECIPIENTS, RecipientId.toSerializedList(recipients))
                                    .putInt(KEY_INITIAL_RECIPIENT_COUNT, initialRecipientCount)
                                    .putLong(KEY_TIMESTAMP, timestamp)
                                    .putString(KEY_GROUP_CONTEXT_V2, Base64.encodeWithPadding(groupContextV2.encode()))
                                    .serialize();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  protected void onRun() throws Exception {
    if (!Recipient.self().isRegistered()) {
      throw new NotPushRegisteredException();
    }

    GroupId.V2 groupId = GroupId.v2(GroupUtil.requireMasterKey(groupContextV2.masterKey.toByteArray()));

    if (Recipient.externalGroupExact(groupId).isBlocked()) {
      Log.i(TAG, "Not updating group state for blocked group " + groupId);
      return;
    }

    List<Recipient> destinations = Stream.of(recipients).map(Recipient::resolved).toList();
    List<Recipient> completions  = deliver(destinations, groupId);

    for (Recipient completion : completions) {
      recipients.remove(completion.getId());
    }

    Log.i(TAG, "Completed now: " + completions.size() + ", Remaining: " + recipients.size());

    if (!recipients.isEmpty()) {
      Log.w(TAG, "Still need to send to " + recipients.size() + " recipients. Retrying.");
      throw new RetryLaterException();
    }
  }

  @Override
  protected boolean onShouldRetry(@NonNull Exception e) {
    if (e instanceof ServerRejectedException) return false;
    if (e instanceof NotPushRegisteredException) return false;
    return e instanceof IOException ||
           e instanceof RetryLaterException;
  }

  @Override
  public void onFailure() {
    Log.w(TAG, "Failed to send remote delete to all recipients! (" + (initialRecipientCount - recipients.size() + "/" + initialRecipientCount + ")") );
  }

  private @NonNull List<Recipient> deliver(@NonNull List<Recipient> destinations, @NonNull GroupId.V2 groupId)
      throws IOException, UntrustedIdentityException
  {
    SignalServiceGroupV2     group            = SignalServiceGroupV2.fromProtobuf(groupContextV2);
    SignalServiceDataMessage groupDataMessage = SignalServiceDataMessage.newBuilder()
                                                                        .withTimestamp(timestamp)
                                                                        .asGroupMessage(group)
                                                                        .build();

    List<SendMessageResult> results = GroupSendUtil.sendUnresendableDataMessage(context, groupId, destinations, false, ContentHint.IMPLICIT, groupDataMessage, false);

    GroupSendJobHelper.SendResult groupResult = GroupSendJobHelper.getCompletedSends(destinations, results);

    for (RecipientId unregistered : groupResult.unregistered) {
      SignalDatabase.recipients().markUnregistered(unregistered);
    }

    return groupResult.completed;
  }

  public static class Factory implements Job.Factory<PushGroupSilentUpdateSendJob> {
    @Override
    public @NonNull PushGroupSilentUpdateSendJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      JsonJobData data = JsonJobData.deserialize(serializedData);

      List<RecipientId> recipients            = RecipientId.fromSerializedList(data.getString(KEY_RECIPIENTS));
      int               initialRecipientCount = data.getInt(KEY_INITIAL_RECIPIENT_COUNT);
      long              timestamp             = data.getLong(KEY_TIMESTAMP);
      byte[]            contextBytes          = Base64.decodeOrThrow(data.getString(KEY_GROUP_CONTEXT_V2));

      GroupContextV2 groupContextV2;
      try {
        groupContextV2 = GroupContextV2.ADAPTER.decode(contextBytes);
      } catch (IOException e) {
        throw new AssertionError(e);
      }

      return new PushGroupSilentUpdateSendJob(recipients, initialRecipientCount, timestamp, groupContextV2, parameters);
    }
  }
}
