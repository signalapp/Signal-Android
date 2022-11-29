package org.thoughtcrime.securesms.jobs;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import org.greenrobot.eventbus.EventBus;
import org.signal.core.util.Hex;
import org.signal.core.util.logging.Log;
import org.signal.libsignal.protocol.InvalidMacException;
import org.signal.libsignal.protocol.InvalidMessageException;
import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.attachments.AttachmentId;
import org.thoughtcrime.securesms.attachments.DatabaseAttachment;
import org.thoughtcrime.securesms.blurhash.BlurHash;
import org.thoughtcrime.securesms.database.AttachmentTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.events.PartProgressEvent;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.JobLogger;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.mms.MmsException;
import org.thoughtcrime.securesms.notifications.v2.ConversationId;
import org.thoughtcrime.securesms.releasechannel.ReleaseChannel;
import org.thoughtcrime.securesms.s3.S3;
import org.thoughtcrime.securesms.transport.RetryLaterException;
import org.thoughtcrime.securesms.util.AttachmentUtil;
import org.thoughtcrime.securesms.util.Base64;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentPointer;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentRemoteId;
import org.whispersystems.signalservice.api.push.exceptions.MissingConfigurationException;
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;
import org.whispersystems.signalservice.api.push.exceptions.RangeException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Okio;

public final class AttachmentDownloadJob extends BaseJob {

  public static final String KEY = "AttachmentDownloadJob";

  private static final int    MAX_ATTACHMENT_SIZE = 150 * 1024  * 1024;
  private static final String TAG                  = Log.tag(AttachmentDownloadJob.class);

  private static final String KEY_MESSAGE_ID    = "message_id";
  private static final String KEY_PART_ROW_ID   = "part_row_id";
  private static final String KEY_PAR_UNIQUE_ID = "part_unique_id";
  private static final String KEY_MANUAL        = "part_manual";

  private long    messageId;
  private long    partRowId;
  private long    partUniqueId;
  private boolean manual;

  public AttachmentDownloadJob(long messageId, AttachmentId attachmentId, boolean manual) {
    this(new Job.Parameters.Builder()
                           .setQueue("AttachmentDownloadJob" + attachmentId.getRowId() + "-" + attachmentId.getUniqueId())
                           .addConstraint(NetworkConstraint.KEY)
                           .setLifespan(TimeUnit.DAYS.toMillis(1))
                           .setMaxAttempts(Parameters.UNLIMITED)
                           .build(),
         messageId,
         attachmentId,
         manual);
  }

  private AttachmentDownloadJob(@NonNull Job.Parameters parameters, long messageId, AttachmentId attachmentId, boolean manual) {
    super(parameters);

    this.messageId    = messageId;
    this.partRowId    = attachmentId.getRowId();
    this.partUniqueId = attachmentId.getUniqueId();
    this.manual       = manual;
  }

  @Override
  public @NonNull Data serialize() {
    return new Data.Builder().putLong(KEY_MESSAGE_ID, messageId)
                             .putLong(KEY_PART_ROW_ID, partRowId)
                             .putLong(KEY_PAR_UNIQUE_ID, partUniqueId)
                             .putBoolean(KEY_MANUAL, manual)
                             .build();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onAdded() {
    Log.i(TAG, "onAdded() messageId: " + messageId + "  partRowId: " + partRowId + "  partUniqueId: " + partUniqueId + "  manual: " + manual);

    final AttachmentTable    database     = SignalDatabase.attachments();
    final AttachmentId       attachmentId = new AttachmentId(partRowId, partUniqueId);
    final DatabaseAttachment attachment   = database.getAttachment(attachmentId);
    final boolean            pending      = attachment != null && attachment.getTransferState() != AttachmentTable.TRANSFER_PROGRESS_DONE
                                                               && attachment.getTransferState() != AttachmentTable.TRANSFER_PROGRESS_PERMANENT_FAILURE;

    if (pending && (manual || AttachmentUtil.isAutoDownloadPermitted(context, attachment))) {
      Log.i(TAG, "onAdded() Marking attachment progress as 'started'");
      database.setTransferState(messageId, attachmentId, AttachmentTable.TRANSFER_PROGRESS_STARTED);
    }
  }

  @Override
  public void onRun() throws Exception {
    doWork();

    if (!SignalDatabase.mms().isStory(messageId)) {
      ApplicationDependencies.getMessageNotifier().updateNotification(context, ConversationId.forConversation(0));
    }
  }

  public void doWork() throws IOException, RetryLaterException {
    Log.i(TAG, "onRun() messageId: " + messageId + "  partRowId: " + partRowId + "  partUniqueId: " + partUniqueId + "  manual: " + manual);

    final AttachmentTable database     = SignalDatabase.attachments();
    final AttachmentId    attachmentId = new AttachmentId(partRowId, partUniqueId);
    final DatabaseAttachment attachment   = database.getAttachment(attachmentId);

    if (attachment == null) {
      Log.w(TAG, "attachment no longer exists.");
      return;
    }

    if (attachment.isPermanentlyFailed()) {
      Log.w(TAG, "Attachment was marked as a permanent failure. Refusing to download.");
      return;
    }

    if (!attachment.isInProgress()) {
      Log.w(TAG, "Attachment was already downloaded.");
      return;
    }

    if (!manual && !AttachmentUtil.isAutoDownloadPermitted(context, attachment)) {
      Log.w(TAG, "Attachment can't be auto downloaded...");
      database.setTransferState(messageId, attachmentId, AttachmentTable.TRANSFER_PROGRESS_PENDING);
      return;
    }

    Log.i(TAG, "Downloading push part " + attachmentId);
    database.setTransferState(messageId, attachmentId, AttachmentTable.TRANSFER_PROGRESS_STARTED);

    if (attachment.getCdnNumber() != ReleaseChannel.CDN_NUMBER) {
      retrieveAttachment(messageId, attachmentId, attachment);
    } else {
      retrieveUrlAttachment(messageId, attachmentId, attachment);
    }
  }

  @Override
  public void onFailure() {
    Log.w(TAG, JobLogger.format(this, "onFailure() messageId: " + messageId + "  partRowId: " + partRowId + "  partUniqueId: " + partUniqueId + "  manual: " + manual));

    final AttachmentId attachmentId = new AttachmentId(partRowId, partUniqueId);
    markFailed(messageId, attachmentId);
  }

  @Override
  protected boolean onShouldRetry(@NonNull Exception exception) {
    return exception instanceof PushNetworkException ||
           exception instanceof RetryLaterException;
  }

  private void retrieveAttachment(long messageId,
                                  final AttachmentId attachmentId,
                                  final Attachment attachment)
      throws IOException, RetryLaterException
  {

    AttachmentTable database       = SignalDatabase.attachments();
    File            attachmentFile = database.getOrCreateTransferFile(attachmentId);

    try {
      SignalServiceMessageReceiver   messageReceiver = ApplicationDependencies.getSignalServiceMessageReceiver();
      SignalServiceAttachmentPointer pointer         = createAttachmentPointer(attachment);
      InputStream                    stream          = messageReceiver.retrieveAttachment(pointer, attachmentFile, MAX_ATTACHMENT_SIZE, (total, progress) -> EventBus.getDefault().postSticky(new PartProgressEvent(attachment, PartProgressEvent.Type.NETWORK, total, progress)));

      database.insertAttachmentsForPlaceholder(messageId, attachmentId, stream);
    } catch (RangeException e) {
      Log.w(TAG, "Range exception, file size " + attachmentFile.length(), e);
      if (attachmentFile.delete()) {
        Log.i(TAG, "Deleted temp download file to recover");
        throw new RetryLaterException(e);
      } else {
        throw new IOException("Failed to delete temp download file following range exception");
      }
    } catch (InvalidPartException | NonSuccessfulResponseCodeException | MmsException | MissingConfigurationException e) {
      Log.w(TAG, "Experienced exception while trying to download an attachment.", e);
      markFailed(messageId, attachmentId);
    } catch (InvalidMessageException e) {
      Log.w(TAG, "Experienced an InvalidMessageException while trying to download an attachment.", e);
      if (e.getCause() instanceof InvalidMacException) {
        Log.w(TAG, "Detected an invalid mac. Treating as a permanent failure.");
        markPermanentlyFailed(messageId, attachmentId);
      } else {
        markFailed(messageId, attachmentId);
      }
    }
  }

  private SignalServiceAttachmentPointer createAttachmentPointer(Attachment attachment) throws InvalidPartException {
    if (TextUtils.isEmpty(attachment.getLocation())) {
      throw new InvalidPartException("empty content id");
    }

    if (TextUtils.isEmpty(attachment.getKey())) {
      throw new InvalidPartException("empty encrypted key");
    }

    try {
      final SignalServiceAttachmentRemoteId remoteId = SignalServiceAttachmentRemoteId.from(attachment.getLocation());
      final byte[]                          key      = Base64.decode(attachment.getKey());

      if (attachment.getDigest() != null) {
        Log.i(TAG, "Downloading attachment with digest: " + Hex.toString(attachment.getDigest()));
      } else {
        Log.i(TAG, "Downloading attachment with no digest...");
      }

      return new SignalServiceAttachmentPointer(attachment.getCdnNumber(), remoteId, null, key,
                                                Optional.of(Util.toIntExact(attachment.getSize())),
                                                Optional.empty(),
                                                0, 0,
                                                Optional.ofNullable(attachment.getDigest()),
                                                Optional.ofNullable(attachment.getFileName()),
                                                attachment.isVoiceNote(),
                                                attachment.isBorderless(),
                                                attachment.isVideoGif(),
                                                Optional.empty(),
                                                Optional.ofNullable(attachment.getBlurHash()).map(BlurHash::getHash),
                                                attachment.getUploadTimestamp());
    } catch (IOException | ArithmeticException e) {
      Log.w(TAG, e);
      throw new InvalidPartException(e);
    }
  }

  private void retrieveUrlAttachment(long messageId,
                                     final AttachmentId attachmentId,
                                     final Attachment attachment)
      throws IOException
  {
    try (Response response = S3.getObject(Objects.requireNonNull(attachment.getFileName()))) {
      ResponseBody body = response.body();
      if (body != null) {
        SignalDatabase.attachments().insertAttachmentsForPlaceholder(messageId, attachmentId, Okio.buffer(body.source()).inputStream());
      }
    } catch (MmsException e) {
      Log.w(TAG, "Experienced exception while trying to download an attachment.", e);
      markFailed(messageId, attachmentId);
    }
  }

  private void markFailed(long messageId, AttachmentId attachmentId) {
    try {
      AttachmentTable database = SignalDatabase.attachments();
      database.setTransferProgressFailed(attachmentId, messageId);
    } catch (MmsException e) {
      Log.w(TAG, e);
    }
  }

  private void markPermanentlyFailed(long messageId, AttachmentId attachmentId) {
    try {
      AttachmentTable database = SignalDatabase.attachments();
      database.setTransferProgressPermanentFailure(attachmentId, messageId);
    } catch (MmsException e) {
      Log.w(TAG, e);
    }
  }

  @VisibleForTesting static class InvalidPartException extends Exception {
    InvalidPartException(String s) {super(s);}
    InvalidPartException(Exception e) {super(e);}
  }

  public static final class Factory implements Job.Factory<AttachmentDownloadJob> {
    @Override
    public @NonNull AttachmentDownloadJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new AttachmentDownloadJob(parameters,
                                       data.getLong(KEY_MESSAGE_ID),
                                       new AttachmentId(data.getLong(KEY_PART_ROW_ID), data.getLong(KEY_PAR_UNIQUE_ID)),
                                       data.getBoolean(KEY_MANUAL));
    }
  }
}
