package org.thoughtcrime.securesms.jobs;

import android.content.Context;
import android.media.MediaDataSource;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.media3.common.MimeTypes;

import org.greenrobot.eventbus.EventBus;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.attachments.AttachmentId;
import org.thoughtcrime.securesms.attachments.DatabaseAttachment;
import org.thoughtcrime.securesms.crypto.AttachmentSecret;
import org.thoughtcrime.securesms.crypto.AttachmentSecretProvider;
import org.thoughtcrime.securesms.crypto.ModernDecryptingPartInputStream;
import org.thoughtcrime.securesms.crypto.ModernEncryptingPartOutputStream;
import org.thoughtcrime.securesms.database.AttachmentTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.events.PartProgressEvent;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.JsonJobData;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.jobmanager.persistence.JobSpec;
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader;
import org.thoughtcrime.securesms.mms.MediaConstraints;
import org.thoughtcrime.securesms.mms.MediaStream;
import org.thoughtcrime.securesms.mms.MmsException;
import org.thoughtcrime.securesms.mms.SentMediaQuality;
import org.thoughtcrime.securesms.service.AttachmentProgressService;
import org.thoughtcrime.securesms.transport.UndeliverableMessageException;
import org.thoughtcrime.securesms.util.BitmapDecodingException;
import org.thoughtcrime.securesms.util.RemoteConfig;
import org.thoughtcrime.securesms.util.ImageCompressionUtil;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.MemoryFileDescriptor.MemoryFileException;
import org.thoughtcrime.securesms.video.InMemoryTranscoder;
import org.thoughtcrime.securesms.video.StreamingTranscoder;
import org.thoughtcrime.securesms.video.TranscoderOptions;
import org.thoughtcrime.securesms.video.exceptions.VideoPostProcessingException;
import org.thoughtcrime.securesms.video.exceptions.VideoSourceException;
import org.thoughtcrime.securesms.video.interfaces.TranscoderCancelationSignal;
import org.thoughtcrime.securesms.video.postprocessing.Mp4FaststartPostProcessor;
import org.thoughtcrime.securesms.video.videoconverter.exceptions.EncodingException;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public final class AttachmentCompressionJob extends BaseJob {

  public static final String KEY = "AttachmentCompressionJob";

  @SuppressWarnings("unused")
  private static final String TAG = Log.tag(AttachmentCompressionJob.class);

  private static final String KEY_ATTACHMENT_ID = "row_id";
  private static final String KEY_MMS           = "mms";
  private static final String KEY_MMS_SUBSCRIPTION_ID = "mms_subscription_id";

  private final AttachmentId attachmentId;
  private final boolean      mms;
  private final int          mmsSubscriptionId;

  public static AttachmentCompressionJob fromAttachment(@NonNull DatabaseAttachment databaseAttachment,
                                                        boolean mms,
                                                        int mmsSubscriptionId)
  {
    return new AttachmentCompressionJob(databaseAttachment.attachmentId,
                                        MediaUtil.isVideo(databaseAttachment) && MediaConstraints.isVideoTranscodeAvailable(),
                                        mms,
                                        mmsSubscriptionId);
  }

  private AttachmentCompressionJob(@NonNull AttachmentId attachmentId,
                                   boolean isVideoTranscode,
                                   boolean mms,
                                   int mmsSubscriptionId)
  {
    this(new Parameters.Builder()
                       .addConstraint(NetworkConstraint.KEY)
                       .setLifespan(TimeUnit.DAYS.toMillis(1))
                       .setMaxAttempts(Parameters.UNLIMITED)
                       .setQueue(isVideoTranscode ? "VIDEO_TRANSCODE" : "GENERIC_TRANSCODE")
                       .build(),
         attachmentId,
         mms,
         mmsSubscriptionId);
  }

  private AttachmentCompressionJob(@NonNull Parameters parameters,
                                   @NonNull AttachmentId attachmentId,
                                   boolean mms,
                                   int mmsSubscriptionId)
  {
    super(parameters);
    this.attachmentId      = attachmentId;
    this.mms               = mms;
    this.mmsSubscriptionId = mmsSubscriptionId;
  }

  @Override
  public @Nullable byte [] serialize() {
    return new JsonJobData.Builder().putLong(KEY_ATTACHMENT_ID, attachmentId.id)
                                    .putBoolean(KEY_MMS, mms)
                                    .putInt(KEY_MMS_SUBSCRIPTION_ID, mmsSubscriptionId)
                                    .serialize();
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
  public void onAdded() {
    Log.i(TAG, "onAdded() " + attachmentId.toString());

    final AttachmentTable    database     = SignalDatabase.attachments();
    final DatabaseAttachment attachment   = database.getAttachment(attachmentId);
    final boolean pending = attachment != null && attachment.transferState != AttachmentTable.TRANSFER_PROGRESS_DONE
                            && attachment.transferState != AttachmentTable.TRANSFER_PROGRESS_PERMANENT_FAILURE;

    if (pending) {
      Log.i(TAG, "onAdded() Marking attachment progress as 'started'");
      database.setTransferState(attachment.mmsId, attachmentId, AttachmentTable.TRANSFER_PROGRESS_STARTED);
    }
  }

  @Override
  public void onRun() throws Exception {
    Log.d(TAG, "Running for: " + attachmentId);

    AttachmentTable    database           = SignalDatabase.attachments();
    DatabaseAttachment databaseAttachment = database.getAttachment(attachmentId);

    if (databaseAttachment == null) {
      throw new UndeliverableMessageException("Cannot find the specified attachment.");
    }

    AttachmentTable.TransformProperties transformProperties = databaseAttachment.transformProperties;

    if (transformProperties == null) {
      Log.i(TAG, "TransformProperties were null! Using empty TransformProperties.");
      transformProperties = AttachmentTable.TransformProperties.empty();
    }

    if (transformProperties.shouldSkipTransform()) {
      Log.i(TAG, "Skipping at the direction of the TransformProperties.");
      return;
    }

    MediaConstraints mediaConstraints = MediaConstraints.getPushMediaConstraints(SentMediaQuality.fromCode(transformProperties.sentMediaQuality));

    compress(database, mediaConstraints, databaseAttachment);
  }

  @Override
  public void onFailure() {
    AttachmentTable    database           = SignalDatabase.attachments();
    DatabaseAttachment databaseAttachment = database.getAttachment(attachmentId);
    if (databaseAttachment == null) {
      Log.i(TAG, "Could not find attachment in DB for compression job upon failure.");
      return;
    }

    try {
      database.setTransferProgressFailed(attachmentId, databaseAttachment.mmsId);
    } catch (MmsException e) {
      Log.w(TAG, "Error marking attachment as failed upon failed compression.", e);
    }
  }

  @Override
  protected boolean onShouldRetry(@NonNull Exception exception) {
    return exception instanceof IOException;
  }

  private void compress(@NonNull AttachmentTable attachmentDatabase,
                        @NonNull MediaConstraints constraints,
                        @NonNull DatabaseAttachment attachment)
      throws UndeliverableMessageException
  {
    try {
      if (attachment.isSticker()) {
        Log.d(TAG, "Sticker, not compressing.");
      } else if (MediaUtil.isVideo(attachment)) {
        Log.i(TAG, "Compressing video.");
        attachment = transcodeVideoIfNeededToDatabase(context, attachmentDatabase, attachment, constraints, EventBus.getDefault(), this::isCanceled);
        if (!constraints.isSatisfied(context, attachment)) {
          throw new UndeliverableMessageException("Size constraints could not be met on video!");
        }
      } else if (constraints.canResize(attachment)) {
        Log.i(TAG, "Compressing image.");
        try (MediaStream converted = compressImage(context, attachment, constraints)) {
          attachmentDatabase.updateAttachmentData(attachment, converted);
        }
        attachmentDatabase.markAttachmentAsTransformed(attachmentId, false);
      } else if (constraints.isSatisfied(context, attachment)) {
        Log.i(TAG, "Not compressing.");
        attachmentDatabase.markAttachmentAsTransformed(attachmentId, false);
      } else {
        throw new UndeliverableMessageException("Size constraints could not be met!");
      }
    } catch (IOException | MmsException e) {
      throw new UndeliverableMessageException(e);
    }
  }

  private static @NonNull DatabaseAttachment transcodeVideoIfNeededToDatabase(@NonNull Context context,
                                                                              @NonNull AttachmentTable attachmentDatabase,
                                                                              @NonNull DatabaseAttachment attachment,
                                                                              @NonNull MediaConstraints constraints,
                                                                              @NonNull EventBus eventBus,
                                                                              @NonNull TranscoderCancelationSignal cancelationSignal)
      throws UndeliverableMessageException
  {
    AttachmentTable.TransformProperties transformProperties = attachment.transformProperties;

    boolean allowSkipOnFailure = false;

    if (!MediaConstraints.isVideoTranscodeAvailable()) {
      if (transformProperties.getVideoEdited()) {
        throw new UndeliverableMessageException("Video edited, but transcode is not available");
      }
      return attachment;
    }

    try (AttachmentProgressService.Controller notification = AttachmentProgressService.start(context, context.getString(R.string.AttachmentUploadJob_compressing_video_start))) {
      if (notification != null) {
        notification.setIndeterminate(true);
      }

      try (MediaDataSource dataSource = attachmentDatabase.mediaDataSourceFor(attachment.attachmentId, false)) {
        if (dataSource == null) {
          throw new UndeliverableMessageException("Cannot get media data source for attachment.");
        }

        TranscoderOptions options = null;
        if (transformProperties != null) {
          allowSkipOnFailure = !transformProperties.getVideoEdited();
          if (transformProperties.videoTrim) {
            options = new TranscoderOptions(transformProperties.videoTrimStartTimeUs, transformProperties.videoTrimEndTimeUs);
          }
        }

        if (RemoteConfig.useStreamingVideoMuxer()) {
          StreamingTranscoder transcoder = new StreamingTranscoder(dataSource, options, constraints.getVideoTranscodingSettings(), constraints.getCompressedVideoMaxSize(context), RemoteConfig.allowAudioRemuxing());

          if (transcoder.isTranscodeRequired()) {
            Log.i(TAG, "Compressing with streaming muxer");
            AttachmentSecret attachmentSecret = AttachmentSecretProvider.getInstance(context).getOrCreateAttachmentSecret();

            File file = AttachmentTable.newDataFile(context);
            file.deleteOnExit();

            boolean faststart = false;
            try {
              try (OutputStream outputStream = ModernEncryptingPartOutputStream.createFor(attachmentSecret, file, true).second) {
                transcoder.transcode(percent -> {
                  if (notification != null) {
                    notification.setProgress(percent / 100f);
                  }
                  eventBus.postSticky(new PartProgressEvent(attachment,
                                                            PartProgressEvent.Type.COMPRESSION,
                                                            100,
                                                            percent));
                }, outputStream, cancelationSignal);
              }

              eventBus.postSticky(new PartProgressEvent(attachment,
                                                        PartProgressEvent.Type.COMPRESSION,
                                                        100,
                                                        100));

              final Mp4FaststartPostProcessor postProcessor = new Mp4FaststartPostProcessor(() -> {
                try {
                  return ModernDecryptingPartInputStream.createFor(attachmentSecret, file, 0);
                } catch (IOException e) {
                  Log.w(TAG, "IOException thrown while creating CipherInputStream.", e);
                  throw new VideoPostProcessingException("Exception while opening InputStream!", e);
                }
              });

              final long plaintextLength = ModernEncryptingPartOutputStream.getPlaintextLength(file.length());
              try (MediaStream mediaStream = new MediaStream(postProcessor.process(plaintextLength), MimeTypes.VIDEO_MP4, 0, 0, true)) {
                attachmentDatabase.updateAttachmentData(attachment, mediaStream);
                faststart = true;
              } catch (VideoPostProcessingException e) {
                Log.w(TAG, "Exception thrown during post processing.", e);
                final Throwable cause = e.getCause();
                if (cause instanceof IOException) {
                  throw (IOException) cause;
                } else if (cause instanceof EncodingException) {
                  throw (EncodingException) cause;
                }
              }

              if (!faststart) {
                try (MediaStream mediaStream = new MediaStream(ModernDecryptingPartInputStream.createFor(attachmentSecret, file, 0), MimeTypes.VIDEO_MP4, 0, 0, false)) {
                  attachmentDatabase.updateAttachmentData(attachment, mediaStream);
                }
              }
            } finally {
              if (!file.delete()) {
                Log.w(TAG, "Failed to delete temp file");
              }
            }

            attachmentDatabase.markAttachmentAsTransformed(attachment.attachmentId, faststart);

            return Objects.requireNonNull(attachmentDatabase.getAttachment(attachment.attachmentId));
          } else {
            Log.i(TAG, "Transcode was not required");
          }
        } else {
          try (InMemoryTranscoder transcoder = new InMemoryTranscoder(context, dataSource, options, constraints.getVideoTranscodingSettings(), constraints.getCompressedVideoMaxSize(context))) {
            if (transcoder.isTranscodeRequired()) {
              Log.i(TAG, "Compressing with android in-memory muxer");

              try (MediaStream mediaStream = transcoder.transcode(percent -> {
                if (notification != null) {
                  notification.setProgress(percent / 100f);
                }
                eventBus.postSticky(new PartProgressEvent(attachment,
                                                          PartProgressEvent.Type.COMPRESSION,
                                                          100,
                                                          percent));
              }, cancelationSignal)) {
                attachmentDatabase.updateAttachmentData(attachment, mediaStream);
                attachmentDatabase.markAttachmentAsTransformed(attachment.attachmentId, mediaStream.getFaststart());
              }

              eventBus.postSticky(new PartProgressEvent(attachment,
                                                        PartProgressEvent.Type.COMPRESSION,
                                                        100,
                                                        100));
              return Objects.requireNonNull(attachmentDatabase.getAttachment(attachment.attachmentId));
            } else {
              Log.i(TAG, "Transcode was not required (in-memory transcoder)");
            }
          }
        }
      }
    } catch (VideoSourceException | EncodingException | MemoryFileException e) {
      if (attachment.size > constraints.getVideoMaxSize(context)) {
        throw new UndeliverableMessageException("Duration not found, attachment too large to skip transcode", e);
      } else {
        if (allowSkipOnFailure) {
          Log.w(TAG, "Problem with video source, but video small enough to skip transcode", e);
        } else {
          throw new UndeliverableMessageException("Failed to transcode and cannot skip due to editing", e);
        }
      }
    } catch (IOException | MmsException e) {
      throw new UndeliverableMessageException("Failed to transcode", e);
    } catch (RuntimeException e) {
      if (e.getCause() instanceof IOException) {
        throw new UndeliverableMessageException("Failed to transcode", e);
      } else {
        throw e;
      }
    }
    return attachment;
  }

  /**
   * Compresses the images. Given that we compress every image, this has the fun side effect of
   * stripping all EXIF data.
   */
  @WorkerThread
  private static MediaStream compressImage(@NonNull Context context,
                                           @NonNull Attachment attachment,
                                           @NonNull MediaConstraints mediaConstraints)
      throws UndeliverableMessageException
  {
    Uri uri = attachment.getUri();

    if (uri == null) {
      throw new UndeliverableMessageException("No attachment URI!");
    }

    ImageCompressionUtil.Result result = null;

    try {
      for (int size : mediaConstraints.getImageDimensionTargets(context)) {
        result = ImageCompressionUtil.compressWithinConstraints(context,
                                                                attachment.contentType,
                                                                new DecryptableStreamUriLoader.DecryptableUri(uri),
                                                                size,
                                                                mediaConstraints.getImageMaxSize(context),
                                                                mediaConstraints.getImageCompressionQualitySetting(context));
        if (result != null) {
          break;
        }
      }
    } catch (BitmapDecodingException e) {
      throw new UndeliverableMessageException(e);
    }

    if (result == null) {
      throw new UndeliverableMessageException("Somehow couldn't meet the constraints!");
    }

    return new MediaStream(new ByteArrayInputStream(result.getData()),
                           result.getMimeType(),
                           result.getWidth(),
                           result.getHeight());
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

  public static final class Factory implements Job.Factory<AttachmentCompressionJob> {
    @Override
    public @NonNull AttachmentCompressionJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      JsonJobData data = JsonJobData.deserialize(serializedData);

      return new AttachmentCompressionJob(parameters,
                                          new AttachmentId(data.getLong(KEY_ATTACHMENT_ID)),
                                          data.getBoolean(KEY_MMS),
                                          data.getInt(KEY_MMS_SUBSCRIPTION_ID));
    }
  }
}
