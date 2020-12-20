package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.attachments.AttachmentId;
import org.thoughtcrime.securesms.database.AttachmentDatabase;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.util.JsonUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Copies the data from one attachment to another. Useful when you only want to send an attachment
 * once, and then copy the data from that upload to other messages.
 */
public class AttachmentCopyJob extends BaseJob {

  public static final String KEY = "AttachmentCopyJob";

  private static final String KEY_SOURCE_ID       = "source_id";
  private static final String KEY_DESTINATION_IDS = "destination_ids";

  private final AttachmentId       sourceId;
  private final List<AttachmentId> destinationIds;

  public AttachmentCopyJob(@NonNull AttachmentId sourceId, @NonNull List<AttachmentId> destinationIds) {
    this(new Job.Parameters.Builder()
                           .setQueue("AttachmentCopyJob")
                           .setMaxAttempts(3)
                           .build(),
        sourceId,
        destinationIds);
  }

  private AttachmentCopyJob(@NonNull Parameters parameters,
                            @NonNull AttachmentId sourceId,
                            @NonNull List<AttachmentId> destinationIds)
  {
    super(parameters);
    this.sourceId       = sourceId;
    this.destinationIds = destinationIds;
  }

  @Override
  public @NonNull Data serialize() {
    try {
      String   sourceIdString       = JsonUtils.toJson(sourceId);
      String[] destinationIdStrings = new String[destinationIds.size()];

      for (int i = 0; i < destinationIds.size(); i++) {
        destinationIdStrings[i] = JsonUtils.toJson(destinationIds.get(i));
      }

      return new Data.Builder().putString(KEY_SOURCE_ID, sourceIdString)
                               .putStringArray(KEY_DESTINATION_IDS, destinationIdStrings)
                               .build();
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  protected boolean shouldTrace() {
    return true;
  }

  @Override
  protected void onRun() throws Exception {
    AttachmentDatabase database = DatabaseFactory.getAttachmentDatabase(context);

    for (AttachmentId destinationId : destinationIds) {
      database.copyAttachmentData(sourceId, destinationId);
    }
  }

  @Override
  protected boolean onShouldRetry(@NonNull Exception e) {
    return true;
  }

  @Override
  public void onFailure() { }

  public static final class Factory implements Job.Factory<AttachmentCopyJob> {
    @Override
    public @NonNull AttachmentCopyJob create(@NonNull Parameters parameters, @NonNull Data data) {
      try {
        String   sourceIdStrings      = data.getString(KEY_SOURCE_ID);
        String[] destinationIdStrings = data.getStringArray(KEY_DESTINATION_IDS);

        AttachmentId       sourceId       = JsonUtils.fromJson(sourceIdStrings, AttachmentId.class);
        List<AttachmentId> destinationIds = new ArrayList<>(destinationIdStrings.length);

        for (String idString : destinationIdStrings) {
          destinationIds.add(JsonUtils.fromJson(idString, AttachmentId.class));
        }

        return new AttachmentCopyJob(parameters, sourceId, destinationIds);
      } catch (IOException e) {
        throw new AssertionError(e);
      }
    }
  }
}
