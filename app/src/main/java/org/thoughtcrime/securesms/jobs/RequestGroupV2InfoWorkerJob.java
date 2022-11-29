package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.GroupTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.groups.GroupChangeBusyException;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.groups.GroupManager;
import org.thoughtcrime.securesms.groups.GroupNotAMemberException;
import org.thoughtcrime.securesms.groups.v2.processing.GroupsV2StateProcessor;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.whispersystems.signalservice.api.groupsv2.NoCredentialForRedemptionTimeException;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Scheduled by {@link RequestGroupV2InfoJob} after message queues are drained.
 */
final class RequestGroupV2InfoWorkerJob extends BaseJob {

  public static final String KEY = "RequestGroupV2InfoWorkerJob";

  private static final String TAG = Log.tag(RequestGroupV2InfoWorkerJob.class);

  private static final String KEY_GROUP_ID    = "group_id";
  private static final String KEY_TO_REVISION = "to_revision";

  private final GroupId.V2 groupId;
  private final int        toRevision;

  @WorkerThread
  RequestGroupV2InfoWorkerJob(@NonNull GroupId.V2 groupId, int toRevision) {
    this(new Parameters.Builder()
                       .setQueue(PushProcessMessageJob.getQueueName(Recipient.externalGroupExact(groupId).getId()))
                       .addConstraint(NetworkConstraint.KEY)
                       .setLifespan(TimeUnit.DAYS.toMillis(1))
                       .setMaxAttempts(Parameters.UNLIMITED)
                       .build(),
         groupId,
         toRevision);
  }

  private RequestGroupV2InfoWorkerJob(@NonNull Parameters parameters, @NonNull GroupId.V2 groupId, int toRevision) {
    super(parameters);

    this.groupId    = groupId;
    this.toRevision = toRevision;
  }

  @Override
  public @NonNull Data serialize() {
    return new Data.Builder().putString(KEY_GROUP_ID, groupId.toString())
                             .putInt(KEY_TO_REVISION, toRevision)
                             .build();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onRun() throws IOException, GroupNotAMemberException, GroupChangeBusyException {
    if (toRevision == GroupsV2StateProcessor.LATEST) {
      Log.i(TAG, "Updating group to latest revision");
    } else {
      Log.i(TAG, "Updating group to revision " + toRevision);
    }

    Optional<GroupTable.GroupRecord> group = SignalDatabase.groups().getGroup(groupId);

    if (!group.isPresent()) {
      Log.w(TAG, "Group not found");
      return;
    }

    if (Recipient.externalGroupExact(groupId).isBlocked()) {
      Log.i(TAG, "Not fetching group info for blocked group " + groupId);
      return;
    }

    GroupManager.updateGroupFromServer(context, group.get().requireV2GroupProperties().getGroupMasterKey(), toRevision, System.currentTimeMillis(), null);
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception e) {
    return e instanceof PushNetworkException ||
           e instanceof NoCredentialForRedemptionTimeException ||
           e instanceof GroupChangeBusyException;
  }

  @Override
  public void onFailure() {
  }

  public static final class Factory implements Job.Factory<RequestGroupV2InfoWorkerJob> {

    @Override
    public @NonNull RequestGroupV2InfoWorkerJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new RequestGroupV2InfoWorkerJob(parameters,
                                             GroupId.parseOrThrow(data.getString(KEY_GROUP_ID)).requireV2(),
                                             data.getInt(KEY_TO_REVISION));
    }
  }
}
