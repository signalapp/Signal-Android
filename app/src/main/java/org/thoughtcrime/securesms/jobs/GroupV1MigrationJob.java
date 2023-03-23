package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.groups.GroupChangeBusyException;
import org.thoughtcrime.securesms.groups.GroupsV1MigrationUtil;
import org.thoughtcrime.securesms.jobmanager.JsonJobData;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.transport.RetryLaterException;
import org.whispersystems.signalservice.api.groupsv2.NoCredentialForRedemptionTimeException;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class GroupV1MigrationJob extends BaseJob {

  private static final String TAG = Log.tag(GroupV1MigrationJob.class);

  public static final String KEY = "GroupV1MigrationJob";

  private static final String KEY_RECIPIENT_ID = "recipient_id";

  private static final int  ROUTINE_LIMIT    = 20;
  private static final long REFRESH_INTERVAL = TimeUnit.HOURS.toMillis(1);

  private final RecipientId recipientId;

  private GroupV1MigrationJob(@NonNull RecipientId recipientId) {
    this(new Parameters.Builder()
                       .setQueue(recipientId.toQueueKey())
                       .setMaxAttempts(Parameters.UNLIMITED)
                       .setLifespan(TimeUnit.DAYS.toMillis(7))
                       .addConstraint(NetworkConstraint.KEY)
                       .build(),
        recipientId);
  }

  private GroupV1MigrationJob(@NonNull Parameters parameters, @NonNull RecipientId recipientId) {
    super(parameters);
    this.recipientId = recipientId;
  }

  public static void enqueuePossibleAutoMigrate(@NonNull RecipientId recipientId) {
    SignalExecutors.BOUNDED.execute(() -> {
      if (Recipient.resolved(recipientId).isPushV1Group()) {
        ApplicationDependencies.getJobManager().add(new GroupV1MigrationJob(recipientId));
      }
    });
  }

  @Override
  public @Nullable byte[] serialize() {
    return new JsonJobData.Builder().putString(KEY_RECIPIENT_ID, recipientId.serialize())
                                    .serialize();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  protected void onRun() throws IOException, GroupChangeBusyException, RetryLaterException {
    if (Recipient.resolved(recipientId).isBlocked()) {
      Log.i(TAG, "Group blocked. Skipping.");
      return;
    }

    try {
      GroupsV1MigrationUtil.migrate(context, recipientId, false);
    } catch (GroupsV1MigrationUtil.InvalidMigrationStateException e) {
      Log.w(TAG, "Invalid migration state. Skipping.");
    }
  }

  @Override
  protected boolean onShouldRetry(@NonNull Exception e) {
    return e instanceof PushNetworkException                   ||
           e instanceof NoCredentialForRedemptionTimeException ||
           e instanceof GroupChangeBusyException               ||
           e instanceof RetryLaterException;
  }

  @Override
  public void onFailure() {
  }

  public static final class Factory implements Job.Factory<GroupV1MigrationJob> {
    @Override
    public @NonNull GroupV1MigrationJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      JsonJobData data = JsonJobData.deserialize(serializedData);
      return new GroupV1MigrationJob(parameters, RecipientId.from(data.getString(KEY_RECIPIENT_ID)));
    }
  }
}
