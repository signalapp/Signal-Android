package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.attachments.AttachmentId;
import org.thoughtcrime.securesms.attachments.DatabaseAttachment;
import org.thoughtcrime.securesms.database.AttachmentTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Only marks an attachment as uploaded.
 */
public final class AttachmentMarkUploadedJob extends BaseJob {

  public static final String KEY = "AttachmentMarkUploadedJob";

  @SuppressWarnings("unused")
  private static final String TAG = Log.tag(AttachmentMarkUploadedJob.class);

  private static final String KEY_ROW_ID     = "row_id";
  private static final String KEY_UNIQUE_ID  = "unique_id";
  private static final String KEY_MESSAGE_ID = "message_id";

  private final AttachmentId attachmentId;
  private final long         messageId;

  public AttachmentMarkUploadedJob(long messageId, @NonNull AttachmentId attachmentId) {
    this(new Parameters.Builder()
                       .setLifespan(TimeUnit.DAYS.toMillis(1))
                       .setMaxAttempts(Parameters.UNLIMITED)
                       .build(),
         messageId,
         attachmentId);
  }

  private AttachmentMarkUploadedJob(@NonNull Parameters parameters, long messageId, @NonNull AttachmentId attachmentId) {
    super(parameters);
    this.attachmentId = attachmentId;
    this.messageId    = messageId;
  }

  @Override
  public @NonNull Data serialize() {
    return new Data.Builder().putLong(KEY_ROW_ID, attachmentId.getRowId())
                             .putLong(KEY_UNIQUE_ID, attachmentId.getUniqueId())
                             .putLong(KEY_MESSAGE_ID, messageId)
                             .build();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onRun() throws Exception {
    AttachmentTable    database           = SignalDatabase.attachments();
    DatabaseAttachment databaseAttachment = database.getAttachment(attachmentId);

    if (databaseAttachment == null) {
      throw new InvalidAttachmentException("Cannot find the specified attachment.");
    }

    database.markAttachmentUploaded(messageId, databaseAttachment);
  }

  @Override
  public void onFailure() {
  }

  @Override
  protected boolean onShouldRetry(@NonNull Exception exception) {
    return exception instanceof IOException;
  }

  private class InvalidAttachmentException extends Exception {
    InvalidAttachmentException(String message) {
      super(message);
    }
  }

  public static final class Factory implements Job.Factory<AttachmentMarkUploadedJob> {
    @Override
    public @NonNull AttachmentMarkUploadedJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new AttachmentMarkUploadedJob(parameters,
                                           data.getLong(KEY_MESSAGE_ID),
                                           new AttachmentId(data.getLong(KEY_ROW_ID), data.getLong(KEY_UNIQUE_ID)));
    }
  }
}
