package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.JobManager;
import org.thoughtcrime.securesms.jobmanager.impl.WebsocketDrainedConstraint;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.service.webrtc.WebRtcData;

/**
 * Allows the enqueueing of one peek operation per group while the web socket is not drained.
 */
public final class GroupCallPeekJob extends BaseJob {

  public static final String KEY = "GroupCallPeekJob";

  private static final String QUEUE = "__GroupCallPeekJob__";

  private static final String KEY_SENDER                    = "sender";
  private static final String KEY_GROUP_RECIPIENT_ID        = "group_recipient_id";
  private static final String KEY_GROUP_CALL_ERA_ID         = "group_call_era_id";
  private static final String KEY_SERVER_RECEIVED_TIMESTAMP = "server_timestamp";

  @NonNull private final WebRtcData.GroupCallUpdateMetadata updateMetadata;

  public static void enqueue(@NonNull WebRtcData.GroupCallUpdateMetadata updateMetadata) {
    JobManager         jobManager = ApplicationDependencies.getJobManager();
    String             queue      = QUEUE + updateMetadata.getGroupRecipientId().serialize();
    Parameters.Builder parameters = new Parameters.Builder()
                                                  .setQueue(queue)
                                                  .addConstraint(WebsocketDrainedConstraint.KEY);

    jobManager.cancelAllInQueue(queue);

    jobManager.add(new GroupCallPeekJob(parameters.build(), updateMetadata));
  }

  private GroupCallPeekJob(@NonNull Parameters parameters,
                           @NonNull WebRtcData.GroupCallUpdateMetadata updateMetadata)
  {
    super(parameters);
    this.updateMetadata = updateMetadata;
  }

  @Override
  protected void onRun() {
    ApplicationDependencies.getJobManager().add(new GroupCallPeekWorkerJob(updateMetadata));
  }

  @Override
  protected boolean onShouldRetry(@NonNull Exception e) {
    return false;
  }

  @Override
  public @NonNull Data serialize() {
    return new Data.Builder()
                   .putString(KEY_SENDER, updateMetadata.getSender().serialize())
                   .putString(KEY_GROUP_RECIPIENT_ID, updateMetadata.getGroupRecipientId().serialize())
                   .putString(KEY_GROUP_CALL_ERA_ID, updateMetadata.getGroupCallEraId())
                   .putLong(KEY_SERVER_RECEIVED_TIMESTAMP, updateMetadata.getServerReceivedTimestamp())
                   .build();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onFailure() {
  }

  public static final class Factory implements Job.Factory<GroupCallPeekJob> {

    @Override
    public @NonNull GroupCallPeekJob create(@NonNull Parameters parameters, @NonNull Data data) {
      RecipientId sender          = RecipientId.from(data.getString(KEY_SENDER));
      RecipientId group           = RecipientId.from(data.getString(KEY_GROUP_RECIPIENT_ID));
      String      era             = data.getString(KEY_GROUP_CALL_ERA_ID);
      long        serverTimestamp = data.getLong(KEY_SERVER_RECEIVED_TIMESTAMP);

      return new GroupCallPeekJob(parameters, new WebRtcData.GroupCallUpdateMetadata(sender, group, era, serverTimestamp));
    }
  }
}
