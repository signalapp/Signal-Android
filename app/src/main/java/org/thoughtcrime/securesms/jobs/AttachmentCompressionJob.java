package org.thoughtcrime.securesms.jobs;

import android.content.Context;
import android.media.MediaDataSource;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.google.android.exoplayer2.util.MimeTypes;

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
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader;
import org.thoughtcrime.securesms.mms.MediaConstraints;
import org.thoughtcrime.securesms.mms.MediaStream;
import org.thoughtcrime.securesms.mms.MmsException;
import org.thoughtcrime.securesms.mms.SentMediaQuality;
import org.thoughtcrime.securesms.service.NotificationController;
import org.thoughtcrime.securesms.transport.UndeliverableMessageException;
import org.thoughtcrime.securesms.util.BitmapDecodingException;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.ImageCompressionUtil;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.MemoryFileDescriptor;
import org.thoughtcrime.securesms.util.MemoryFileDescriptor.MemoryFileException;
import org.thoughtcrime.securesms.video.InMemoryTranscoder;
import org.thoughtcrime.securesms.video.StreamingTranscoder;
import org.thoughtcrime.securesms.video.TranscoderCancelationSignal;
import org.thoughtcrime.securesms.video.TranscoderOptions;
import org.thoughtcrime.securesms.video.VideoSourceException;
import org.thoughtcrime.securesms.video.videoconverter.EncodingException;

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

  private static final String KEY_ROW_ID              = "row_id";
  private static final String KEY_UNIQUE_ID           = "unique_id";
  private static final String KEY_MMS                 = "mms";
  private static final String KEY_MMS_SUBSCRIPTION_ID = "mms_subscription_id";

  private final AttachmentId attachmentId;
  private final boolean      mms;
  private final int          mmsSubscriptionId;

  public static AttachmentCompressionJob fromAttachment(@NonNull DatabaseAttachment databaseAttachment,
                                                        boolean mms,
                                                        int mmsSubscriptionId)
  {
    return new AttachmentCompressionJob(databaseAttachment.getAttachmentId(),
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
  public @NonNull Data serialize() {
    return new Data.Builder().putLong(KEY_ROW_ID, attachmentId.getRowId())
                             .putLong(KEY_UNIQUE_ID, attachmentId.getUniqueId())
                             .putBoolean(KEY_MMS, mms)
                             .putInt(KEY_MMS_SUBSCRIPTION_ID, mmsSubscriptionId)
                             .build();
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
  public void onRun() throws Exception {
    Log.d(TAG, "Running for: " + attachmentId);

    AttachmentTable    database           = SignalDatabase.attachments();
    DatabaseAttachment databaseAttachment = database.getAttachment(attachmentId);

    if (databaseAttachment == null) {
      throw new UndeliverableMessageException("Cannot find the specified attachment.");
    }

    if (databaseAttachment.getTransformProperties().shouldSkipTransform()) {
      Log.i(TAG, "Skipping at the direction of the TransformProperties.");
      return;
    }

    MediaConstraints mediaConstraints = mms ? MediaConstraints.getMmsMediaConstraints(mmsSubscriptionId)
                                            : MediaConstraints.getPushMediaConstraints(SentMediaQuality.fromCode(databaseAttachment.getTransformProperties().getSentMediaQuality()));

    compress(database, mediaConstraints, databaseAttachment);
  }

  @Override
  public void onFailure() { }

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
          attachmentDatabase.updateAttachmentData(attachment, converted, false);
        }
        attachmentDatabase.markAttachmentAsTransformed(attachmentId);
      } else if (constraints.isSatisfied(context, attachment)) {
        Log.i(TAG, "Not compressing.");
        attachmentDatabase.markAttachmentAsTransformed(attachmentId);
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
    AttachmentTable.TransformProperties transformProperties = attachment.getTransformProperties();

    boolean allowSkipOnFailure = false;

    if (!MediaConstraints.isVideoTranscodeAvailable()) {
      if (transformProperties.isVideoEdited()) {
        throw new UndeliverableMessageException("Video edited, but transcode is not available");
      }
      return attachment;
    }

    try (NotificationController notification = ForegroundServiceUtil.startGenericTaskWhenCapable(context, context.getString(R.string.AttachmentUploadJob_compressing_video_start))) {

      notification.setIndeterminateProgress();

      try (MediaDataSource dataSource = attachmentDatabase.mediaDataSourceFor(attachment.getAttachmentId())) {
        if (dataSource == null) {
          throw new UndeliverableMessageException("Cannot get media data source for attachment.");
        }

        allowSkipOnFailure = !transformProperties.isVideoEdited();
        TranscoderOptions options = null;
        if (transformProperties.isVideoTrim()) {
          options = new TranscoderOptions(transformProperties.getVideoTrimStartTimeUs(), transformProperties.getVideoTrimEndTimeUs());
        }

        if (FeatureFlags.useStreamingVideoMuxer() || !MemoryFileDescriptor.supported()) {
          StreamingTranscoder transcoder = new StreamingTranscoder(dataSource, options, constraints.getCompressedVideoMaxSize(context));

          if (transcoder.isTranscodeRequired()) {
            Log.i(TAG, "Compressing with streaming muxer");
            AttachmentSecret attachmentSecret = AttachmentSecretProvider.getInstance(context).getOrCreateAttachmentSecret();

            File file = SignalDatabase.attachments().newFile();
            file.deleteOnExit();

            try {
              try (OutputStream outputStream = ModernEncryptingPartOutputStream.createFor(attachmentSecret, file, true).second) {
                transcoder.transcode(percent -> {
                  notification.setProgress(100, percent);
                  eventBus.postSticky(new PartProgressEvent(attachment,
                                                            PartProgressEvent.Type.COMPRESSION,
                                                            100,
                                                            percent));
                }, outputStream, cancelationSignal);
              }

              try (MediaStream mediaStream = new MediaStream(ModernDecryptingPartInputStream.createFor(attachmentSecret, file, 0), MimeTypes.VIDEO_MP4, 0, 0)) {
                attachmentDatabase.updateAttachmentData(attachment, mediaStream, true);
              }
            } finally {
              if (!file.delete()) {
                Log.w(TAG, "Failed to delete temp file");
              }
            }

            attachmentDatabase.markAttachmentAsTransformed(attachment.getAttachmentId());

            return Objects.requireNonNull(attachmentDatabase.getAttachment(attachment.getAttachmentId()));
          } else {
            Log.i(TAG, "Transcode was not required");
          }
        } else {
          try (InMemoryTranscoder transcoder = new InMemoryTranscoder(context, dataSource, options, constraints.getCompressedVideoMaxSize(context))) {
            if (transcoder.isTranscodeRequired()) {
              Log.i(TAG, "Compressing with android in-memory muxer");

              try (MediaStream mediaStream = transcoder.transcode(percent -> {
                notification.setProgress(100, percent);
                eventBus.postSticky(new PartProgressEvent(attachment,
                                                          PartProgressEvent.Type.COMPRESSION,
                                                          100,
                                                          percent));
              }, cancelationSignal)) {
                attachmentDatabase.updateAttachmentData(attachment, mediaStream, true);
              }

              attachmentDatabase.markAttachmentAsTransformed(attachment.getAttachmentId());

              return Objects.requireNonNull(attachmentDatabase.getAttachment(attachment.getAttachmentId()));
            } else {
              Log.i(TAG, "Transcode was not required (in-memory transcoder)");
            }
          }
        }
      }
    } catch (VideoSourceException | EncodingException | MemoryFileException e) {
      if (attachment.getSize() > constraints.getVideoMaxSize(context)) {
        throw new UndeliverableMessageException("Duration not found, attachment too large to skip transcode", e);
      } else {
        if (allowSkipOnFailure) {
          Log.w(TAG, "Problem with video source, but video small enough to skip transcode", e);
        } else {
          throw new UndeliverableMessageException("Failed to transcode and cannot skip due to editing", e);
        }
      }
    } catch (UnableToStartException | IOException | MmsException e) {
      throw new UndeliverableMessageException("Failed to transcode", e);
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
                                                                attachment.getContentType(),
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

  public static final class Factory implements Job.Factory<AttachmentCompressionJob> {
    @Override
    public @NonNull AttachmentCompressionJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new AttachmentCompressionJob(parameters,
                                          new AttachmentId(data.getLong(KEY_ROW_ID), data.getLong(KEY_UNIQUE_ID)),
                                          data.getBoolean(KEY_MMS),
                                          data.getInt(KEY_MMS_SUBSCRIPTION_ID));
    }
  }
}
