/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.JobLogger;
import org.thoughtcrime.securesms.jobmanager.JsonJobData;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.jobmanager.persistence.JobSpec;
import org.thoughtcrime.securesms.mms.MmsException;
import org.thoughtcrime.securesms.notifications.v2.ConversationId;
import org.thoughtcrime.securesms.releasechannel.ReleaseChannel;
import org.thoughtcrime.securesms.s3.S3;
import org.thoughtcrime.securesms.transport.RetryLaterException;
import org.thoughtcrime.securesms.util.AttachmentUtil;
import org.signal.core.util.Base64;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
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

  private static final String TAG = Log.tag(AttachmentDownloadJob.class);

  private static final String KEY_MESSAGE_ID    = "message_id";
  private static final String KEY_ATTACHMENT_ID = "part_row_id";
  private static final String KEY_MANUAL        = "part_manual";

  private final long    messageId;
  private final long    attachmentId;
  private final boolean manual;

  public AttachmentDownloadJob(long messageId, AttachmentId attachmentId, boolean manual) {
    this(new Job.Parameters.Builder()
             .setQueue(constructQueueString(attachmentId))
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
    this.attachmentId = attachmentId.id;
    this.manual       = manual;
  }

  @Override
  public @Nullable byte[] serialize() {
    return new JsonJobData.Builder().putLong(KEY_MESSAGE_ID, messageId)
                                    .putLong(KEY_ATTACHMENT_ID, attachmentId)
                                    .putBoolean(KEY_MANUAL, manual)
                                    .serialize();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  public static String constructQueueString(AttachmentId attachmentId) {
    return "AttachmentDownloadJob-" + attachmentId.id;
  }

  @Override
  public void onAdded() {
    Log.i(TAG, "onAdded() messageId: " + messageId + "  attachmentId: " + attachmentId + "  manual: " + manual);

    final AttachmentTable    database     = SignalDatabase.attachments();
    final AttachmentId       attachmentId = new AttachmentId(this.attachmentId);
    final DatabaseAttachment attachment   = database.getAttachment(attachmentId);
    final boolean            pending      = attachment != null && attachment.transferState != AttachmentTable.TRANSFER_PROGRESS_DONE
                                            && attachment.transferState != AttachmentTable.TRANSFER_PROGRESS_PERMANENT_FAILURE;

    if (pending && (manual || AttachmentUtil.isAutoDownloadPermitted(context, attachment))) {
      Log.i(TAG, "onAdded() Marking attachment progress as 'started'");
      database.setTransferState(messageId, attachmentId, AttachmentTable.TRANSFER_PROGRESS_STARTED);
    }
  }

  @Override
  public void onRun() throws Exception {
    doWork();

    if (!SignalDatabase.messages().isStory(messageId)) {
      ApplicationDependencies.getMessageNotifier().updateNotification(context, ConversationId.forConversation(0));
    }
  }

  public void doWork() throws IOException, RetryLaterException {
    Log.i(TAG, "onRun() messageId: " + messageId + "  attachmentId: " + attachmentId + "  manual: " + manual);

    final AttachmentTable    database     = SignalDatabase.attachments();
    final AttachmentId       attachmentId = new AttachmentId(this.attachmentId);
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

    if (attachment.cdnNumber != ReleaseChannel.CDN_NUMBER) {
      retrieveAttachment(messageId, attachmentId, attachment);
    } else {
      retrieveAttachmentForReleaseChannel(messageId, attachmentId, attachment);
    }
  }

  @Override
  public void onFailure() {
    Log.w(TAG, JobLogger.format(this, "onFailure() messageId: " + messageId + "  attachmentId: " + attachmentId + "  manual: " + manual));

    final AttachmentId attachmentId = new AttachmentId(this.attachmentId);
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
    long maxReceiveSize = FeatureFlags.maxAttachmentReceiveSizeBytes();

    AttachmentTable database       = SignalDatabase.attachments();
    File            attachmentFile = database.getOrCreateTransferFile(attachmentId);

    try {
      if (attachment.size > maxReceiveSize) {
        throw new MmsException("Attachment too large, failing download");
      }
      SignalServiceMessageReceiver   messageReceiver = ApplicationDependencies.getSignalServiceMessageReceiver();
      SignalServiceAttachmentPointer pointer         = createAttachmentPointer(attachment);
      InputStream stream = messageReceiver.retrieveAttachment(pointer,
                                                              attachmentFile,
                                                              maxReceiveSize,
                                                              new SignalServiceAttachment.ProgressListener() {
                                                                @Override
                                                                public void onAttachmentProgress(long total, long progress) {
                                                                  EventBus.getDefault().postSticky(new PartProgressEvent(attachment, PartProgressEvent.Type.NETWORK, total, progress));
                                                                }

                                                                @Override
                                                                public boolean shouldCancel() {
                                                                  return isCanceled();
                                                                }
                                                              });
      database.finalizeAttachmentAfterDownload(messageId, attachmentId, stream);
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
    if (TextUtils.isEmpty(attachment.remoteLocation)) {
      throw new InvalidPartException("empty content id");
    }

    if (TextUtils.isEmpty(attachment.remoteKey)) {
      throw new InvalidPartException("empty encrypted key");
    }

    try {
      final SignalServiceAttachmentRemoteId remoteId = SignalServiceAttachmentRemoteId.from(attachment.remoteLocation);
      final byte[]                          key      = Base64.decode(attachment.remoteKey);

      if (attachment.remoteDigest != null) {
        Log.i(TAG, "Downloading attachment with digest: " + Hex.toString(attachment.remoteDigest));
      } else {
        Log.i(TAG, "Downloading attachment with no digest...");
      }

      return new SignalServiceAttachmentPointer(attachment.cdnNumber, remoteId, null, key,
                                                Optional.of(Util.toIntExact(attachment.size)),
                                                Optional.empty(),
                                                0, 0,
                                                Optional.ofNullable(attachment.remoteDigest),
                                                Optional.ofNullable(attachment.getIncrementalDigest()),
                                                attachment.incrementalMacChunkSize,
                                                Optional.ofNullable(attachment.fileName),
                                                attachment.voiceNote,
                                                attachment.borderless,
                                                attachment.videoGif,
                                                Optional.empty(),
                                                Optional.ofNullable(attachment.blurHash).map(BlurHash::getHash),
                                                attachment.uploadTimestamp);
    } catch (IOException | ArithmeticException e) {
      Log.w(TAG, e);
      throw new InvalidPartException(e);
    }
  }

  private void retrieveAttachmentForReleaseChannel(long messageId,
                                                   final AttachmentId attachmentId,
                                                   final Attachment attachment)
      throws IOException
  {
    try (Response response = S3.getObject(Objects.requireNonNull(attachment.fileName))) {
      ResponseBody body = response.body();
      if (body != null) {
        if (body.contentLength() > FeatureFlags.maxAttachmentReceiveSizeBytes()) {
          throw new MmsException("Attachment too large, failing download");
        }
        SignalDatabase.attachments().finalizeAttachmentAfterDownload(messageId, attachmentId, Okio.buffer(body.source()).inputStream());
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

  public static boolean jobSpecMatchesAttachmentId(@NonNull JobSpec jobSpec, @NonNull AttachmentId attachmentId) {
    if (!KEY.equals(jobSpec.getFactoryKey())) {
      return false;
    }

    final byte[] serializedData = jobSpec.getSerializedData();
    if (serializedData == null) {
      return false;
    }

    JsonJobData data = JsonJobData.deserialize(serializedData);
    final AttachmentId parsed = new AttachmentId(data.getLong(KEY_ATTACHMENT_ID));
    return attachmentId.equals(parsed);
  }

  @VisibleForTesting
  static class InvalidPartException extends Exception {
    InvalidPartException(String s) {super(s);}
    InvalidPartException(Exception e) {super(e);}
  }

  public static final class Factory implements Job.Factory<AttachmentDownloadJob> {
    @Override
    public @NonNull AttachmentDownloadJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      JsonJobData data = JsonJobData.deserialize(serializedData);

      return new AttachmentDownloadJob(parameters,
                                       data.getLong(KEY_MESSAGE_ID),
                                       new AttachmentId(data.getLong(KEY_ATTACHMENT_ID)),
                                       data.getBoolean(KEY_MANUAL));
    }
  }
}
