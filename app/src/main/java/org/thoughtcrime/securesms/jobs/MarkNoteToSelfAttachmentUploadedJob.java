package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.attachments.AttachmentId;
import org.thoughtcrime.securesms.attachments.DatabaseAttachment;
import org.thoughtcrime.securesms.database.AttachmentTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.jobmanager.JsonJobData;
import org.thoughtcrime.securesms.jobmanager.Job;

import java.util.concurrent.TimeUnit;

/**
 * Marks a note to self attachment (that didn't need to be uploaded, because there's no linked devices) as being uploaded for UX purposes.
 * Also generates a key/iv/digest that otherwise wouldn't exist due to the lack of upload.
 */
public final class MarkNoteToSelfAttachmentUploadedJob extends BaseJob {

  public static final String KEY = "AttachmentMarkUploadedJob";

  @SuppressWarnings("unused")
  private static final String TAG = Log.tag(MarkNoteToSelfAttachmentUploadedJob.class);

  private static final String KEY_ATTACHMENT_ID = "row_id";
  private static final String KEY_MESSAGE_ID    = "message_id";

  private final AttachmentId attachmentId;
  private final long         messageId;

  public MarkNoteToSelfAttachmentUploadedJob(long messageId, @NonNull AttachmentId attachmentId) {
    this(new Parameters.Builder()
                       .setLifespan(TimeUnit.DAYS.toMillis(1))
                       .setMaxAttempts(Parameters.UNLIMITED)
                       .build(),
         messageId,
         attachmentId);
  }

  private MarkNoteToSelfAttachmentUploadedJob(@NonNull Parameters parameters, long messageId, @NonNull AttachmentId attachmentId) {
    super(parameters);
    this.attachmentId = attachmentId;
    this.messageId    = messageId;
  }

  @Override
  public @Nullable byte[] serialize() {
    return new JsonJobData.Builder().putLong(KEY_ATTACHMENT_ID, attachmentId.id)
                                    .putLong(KEY_MESSAGE_ID, messageId)
                                    .serialize();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onRun() throws Exception {
    DatabaseAttachment databaseAttachment = SignalDatabase.attachments().getAttachment(attachmentId);

    if (databaseAttachment == null) {
      throw new InvalidAttachmentException("Cannot find the specified attachment.");
    }

    SignalDatabase.attachments().markAttachmentUploaded(messageId, databaseAttachment);
    SignalDatabase.attachments().createKeyIvDigestIfNecessary(databaseAttachment);
  }

  @Override
  public void onFailure() {
  }

  @Override
  protected boolean onShouldRetry(@NonNull Exception exception) {
    return false;
  }

  private class InvalidAttachmentException extends Exception {
    InvalidAttachmentException(String message) {
      super(message);
    }
  }

  public static final class Factory implements Job.Factory<MarkNoteToSelfAttachmentUploadedJob> {
    @Override
    public @NonNull MarkNoteToSelfAttachmentUploadedJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      JsonJobData data = JsonJobData.deserialize(serializedData);

      return new MarkNoteToSelfAttachmentUploadedJob(parameters,
                                                     data.getLong(KEY_MESSAGE_ID),
                                                     new AttachmentId(data.getLong(KEY_ATTACHMENT_ID)));
    }
  }
}
