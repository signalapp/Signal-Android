package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.groups.GroupChangeBusyException;
import org.thoughtcrime.securesms.groups.GroupChangeFailedException;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.groups.GroupInsufficientRightsException;
import org.thoughtcrime.securesms.groups.GroupManager;
import org.thoughtcrime.securesms.groups.GroupNotAMemberException;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.whispersystems.signalservice.api.groupsv2.NoCredentialForRedemptionTimeException;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * When your profile key changes, this job can be used to update it on a single given group.
 * <p>
 * Your membership is confirmed first, so safe to run against any known {@link GroupId.V2}
 */
public final class GroupV2UpdateSelfProfileKeyJob extends BaseJob {

  public static final String KEY = "GroupV2UpdateSelfProfileKeyJob";

  private static final String QUEUE = "GroupV2UpdateSelfProfileKeyJob";

  @SuppressWarnings("unused")
  private static final String TAG = Log.tag(GroupV2UpdateSelfProfileKeyJob.class);

  private static final String KEY_GROUP_ID = "group_id";

  private final GroupId.V2 groupId;

  public GroupV2UpdateSelfProfileKeyJob(@NonNull GroupId.V2 groupId) {
    this(new Parameters.Builder()
                       .addConstraint(NetworkConstraint.KEY)
                       .setLifespan(TimeUnit.DAYS.toMillis(1))
                       .setMaxAttempts(Parameters.UNLIMITED)
                       .setQueue(QUEUE)
                       .build(),
         groupId);
  }

  private GroupV2UpdateSelfProfileKeyJob(@NonNull Parameters parameters, @NonNull GroupId.V2 groupId) {
    super(parameters);
    this.groupId = groupId;
  }

  @Override
  public @NonNull Data serialize() {
    return new Data.Builder().putString(KEY_GROUP_ID, groupId.toString())
                             .build();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onRun()
      throws IOException, GroupNotAMemberException, GroupChangeFailedException, GroupInsufficientRightsException, GroupChangeBusyException
  {
    Log.i(TAG, "Ensuring profile key up to date on group " + groupId);
    GroupManager.updateSelfProfileKeyInGroup(context, groupId);
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception e) {
    return e instanceof PushNetworkException ||
           e instanceof NoCredentialForRedemptionTimeException||
           e instanceof GroupChangeBusyException;
  }

  @Override
  public void onFailure() {
  }

  public static final class Factory implements Job.Factory<GroupV2UpdateSelfProfileKeyJob> {

    @Override
    public @NonNull GroupV2UpdateSelfProfileKeyJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new GroupV2UpdateSelfProfileKeyJob(parameters,
                                                GroupId.parseOrThrow(data.getString(KEY_GROUP_ID)).requireV2());
    }
  }
}
