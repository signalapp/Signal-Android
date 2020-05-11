package org.thoughtcrime.securesms.jobs;

import android.content.Context;

import androidx.annotation.NonNull;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.crypto.UnidentifiedAccessUtil;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.recipients.RecipientUtil;
import org.thoughtcrime.securesms.transport.RetryLaterException;
import org.thoughtcrime.securesms.util.Base64;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccessPair;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.SendMessageResult;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceGroup;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Normally, we can do group leaves via {@link PushGroupSendJob}. However, that job relies on a
 * message being present in the database, which is not true if the user selects a message request
 * option that deletes and leaves at the same time.
 *
 * This job tracks all send state within the job and does not require a message in the database to
 * work.
 */
public class LeaveGroupJob extends BaseJob {

  public static final String KEY = "LeaveGroupJob";

  private static final String TAG = Log.tag(LeaveGroupJob.class);

  private static final String KEY_GROUP_ID   = "group_id";
  private static final String KEY_GROUP_NAME = "name";
  private static final String KEY_MEMBERS    = "members";
  private static final String KEY_RECIPIENTS = "recipients";

  private final GroupId.Push      groupId;
  private final String            name;
  private final List<RecipientId> members;
  private final List<RecipientId> recipients;

  public static @NonNull LeaveGroupJob create(@NonNull Recipient group) {
    List<RecipientId> members = Stream.of(group.resolve().getParticipants()).map(Recipient::getId).toList();
    members.remove(Recipient.self().getId());

    return new LeaveGroupJob(group.getGroupId().get().requirePush(),
                             group.resolve().getDisplayName(ApplicationDependencies.getApplication()),
                             members,
                             members,
                             new Parameters.Builder()
                                           .setQueue(group.getId().toQueueKey())
                                           .addConstraint(NetworkConstraint.KEY)
                                           .setLifespan(TimeUnit.DAYS.toMillis(1))
                                           .setMaxAttempts(Parameters.UNLIMITED)
                                           .build());
  }

  private LeaveGroupJob(@NonNull GroupId.Push groupId,
                        @NonNull String name,
                        @NonNull List<RecipientId> members,
                        @NonNull List<RecipientId> recipients,
                        @NonNull Parameters parameters)
  {
    super(parameters);
    this.groupId    = groupId;
    this.name       = name;
    this.members    = Collections.unmodifiableList(members);
    this.recipients = recipients;
  }

  @Override
  public @NonNull Data serialize() {
    return new Data.Builder().putString(KEY_GROUP_ID, Base64.encodeBytes(groupId.getDecodedId()))
                             .putString(KEY_GROUP_NAME, name)
                             .putString(KEY_MEMBERS, RecipientId.toSerializedList(members))
                             .putString(KEY_RECIPIENTS, RecipientId.toSerializedList(recipients))
                             .build();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  protected void onRun() throws Exception {
    List<Recipient> completions = deliver(context, groupId, name, members, recipients);

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
    return e instanceof IOException || e instanceof RetryLaterException;
  }

  @Override
  public void onFailure() {
  }

  private static @NonNull List<Recipient> deliver(@NonNull Context context,
                                                  @NonNull GroupId groupId,
                                                  @NonNull String name,
                                                  @NonNull List<RecipientId> members,
                                                  @NonNull List<RecipientId> destinations)
      throws IOException, UntrustedIdentityException
  {
    SignalServiceMessageSender             messageSender      = ApplicationDependencies.getSignalServiceMessageSender();
    List<SignalServiceAddress>             addresses          = Stream.of(destinations).map(Recipient::resolved).map(t -> RecipientUtil.toSignalServiceAddress(context, t)).toList();
    List<SignalServiceAddress>             memberAddresses    = Stream.of(members).map(Recipient::resolved).map(t -> RecipientUtil.toSignalServiceAddress(context, t)).toList();
    List<Optional<UnidentifiedAccessPair>> unidentifiedAccess = Stream.of(destinations).map(Recipient::resolved).map(recipient -> UnidentifiedAccessUtil.getAccessFor(context, recipient)).toList();
    SignalServiceGroup                     serviceGroup       = new SignalServiceGroup(SignalServiceGroup.Type.QUIT, groupId.getDecodedId(), name, memberAddresses, null);
    SignalServiceDataMessage.Builder       dataMessage        = SignalServiceDataMessage.newBuilder()
                                                                                        .withTimestamp(System.currentTimeMillis())
                                                                                        .asGroupMessage(serviceGroup);


    List<SendMessageResult> results = messageSender.sendMessage(addresses, unidentifiedAccess, false, dataMessage.build());

    Stream.of(results)
          .filter(r -> r.getIdentityFailure() != null)
          .map(SendMessageResult::getAddress)
          .map(a -> Recipient.externalPush(context, a))
          .forEach(r -> Log.w(TAG, "Identity failure for " + r.getId()));

    Stream.of(results)
          .filter(SendMessageResult::isUnregisteredFailure)
          .map(SendMessageResult::getAddress)
          .map(a -> Recipient.externalPush(context, a))
          .forEach(r -> Log.w(TAG, "Unregistered failure for " + r.getId()));


    return Stream.of(results)
                 .filter(r -> r.getSuccess() != null || r.getIdentityFailure() != null || r.isUnregisteredFailure())
                 .map(SendMessageResult::getAddress)
                 .map(a -> Recipient.externalPush(context, a))
                 .toList();
  }

  public static class Factory implements Job.Factory<LeaveGroupJob> {
    @Override
    public @NonNull LeaveGroupJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new LeaveGroupJob(GroupId.v1orThrow(Base64.decodeOrThrow(data.getString(KEY_GROUP_ID))),
                               data.getString(KEY_GROUP_NAME),
                               RecipientId.fromSerializedList(data.getString(KEY_MEMBERS)),
                               RecipientId.fromSerializedList(data.getString(KEY_RECIPIENTS)),
                               parameters);
    }
  }
}
