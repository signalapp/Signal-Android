package org.thoughtcrime.securesms.jobs;

import android.content.Context;
import android.media.MediaDataSource;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.attachments.DatabaseAttachment;
import org.thoughtcrime.securesms.database.AttachmentDatabase;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader;
import org.thoughtcrime.securesms.mms.MediaConstraints;
import org.thoughtcrime.securesms.mms.MediaStream;
import org.thoughtcrime.securesms.mms.MmsException;
import org.thoughtcrime.securesms.service.GenericForegroundService;
import org.thoughtcrime.securesms.service.NotificationController;
import org.thoughtcrime.securesms.transport.UndeliverableMessageException;
import org.thoughtcrime.securesms.util.BitmapDecodingException;
import org.thoughtcrime.securesms.util.BitmapUtil;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.video.InMemoryTranscoder;
import org.thoughtcrime.securesms.video.VideoSourceException;
import org.thoughtcrime.securesms.video.VideoSizeException;
import org.thoughtcrime.securesms.video.videoconverter.EncodingException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

final class MediaResizer {

  private static final String TAG = Log.tag(MediaResizer.class);

  @NonNull private final Context          context;
  @NonNull private final MediaConstraints constraints;

  MediaResizer(@NonNull Context context,
               @NonNull MediaConstraints constraints)
  {
    this.context     = context;
    this.constraints = constraints;
  }

  List<Attachment> scaleAndStripExifToDatabase(@NonNull AttachmentDatabase attachmentDatabase,
                                               @NonNull List<Attachment> attachments)
      throws UndeliverableMessageException
  {
    List<Attachment> results = new ArrayList<>(attachments.size());

    for (Attachment attachment : attachments) {
      results.add(scaleAndStripExifToDatabase(attachmentDatabase, (DatabaseAttachment) attachment, null));
    }

    return results;
  }

  DatabaseAttachment scaleAndStripExifToDatabase(@NonNull AttachmentDatabase attachmentDatabase,
                                                 @NonNull DatabaseAttachment attachment,
                                                 @Nullable ProgressListener transcodeProgressListener)
      throws UndeliverableMessageException
  {
    try {
      if (MediaUtil.isVideo(attachment) && MediaConstraints.isVideoTranscodeAvailable()) {
        return transcodeVideoIfNeededToDatabase(attachmentDatabase, attachment, transcodeProgressListener);
      } else if (constraints.isSatisfied(context, attachment)) {
        if (MediaUtil.isJpeg(attachment)) {
          MediaStream stripped = getResizedMedia(context, attachment);
          return attachmentDatabase.updateAttachmentData(attachment, stripped);
        } else {
          return attachment;
        }
      } else if (constraints.canResize(attachment)) {
        MediaStream resized = getResizedMedia(context, attachment);
        return attachmentDatabase.updateAttachmentData(attachment, resized);
      } else {
        throw new UndeliverableMessageException("Size constraints could not be met!");
      }
    } catch (IOException | MmsException e) {
      throw new UndeliverableMessageException(e);
    }
  }

  @RequiresApi(26)
  private @NonNull DatabaseAttachment transcodeVideoIfNeededToDatabase(@NonNull AttachmentDatabase attachmentDatabase,
                                                                       @NonNull DatabaseAttachment attachment,
                                                                       @Nullable ProgressListener progressListener)
      throws UndeliverableMessageException
  {
    try (NotificationController notification = GenericForegroundService.startForegroundTask(context, context.getString(R.string.AttachmentUploadJob_compressing_video_start))) {

      notification.setIndeterminateProgress();

      try (MediaDataSource dataSource = attachmentDatabase.mediaDataSourceFor(attachment.getAttachmentId())) {

        if (dataSource == null) {
          throw new UndeliverableMessageException("Cannot get media data source for attachment.");
        }

        try (InMemoryTranscoder transcoder = new InMemoryTranscoder(context, dataSource, constraints.getCompressedVideoMaxSize(context))) {

          if (transcoder.isTranscodeRequired()) {

            MediaStream mediaStream = transcoder.transcode(percent -> {
              notification.setProgress(100, percent);

              if (progressListener != null) {
                progressListener.onProgress(percent, 100);
              }
            });

            return attachmentDatabase.updateAttachmentData(attachment, mediaStream);
          } else {
            return attachment;
          }
        }
      }
    } catch (VideoSourceException | EncodingException e) {
      if (attachment.getSize() > constraints.getVideoMaxSize(context)) {
        throw new UndeliverableMessageException("Duration not found, attachment too large to skip transcode", e);
      } else {
        Log.w(TAG, "Duration not found, video small enough to skip transcode", e);
        return attachment;
      }
    } catch (IOException | MmsException | VideoSizeException e) {
      throw new UndeliverableMessageException("Failed to transcode", e);
    }
  }

  private MediaStream getResizedMedia(@NonNull Context context, @NonNull Attachment attachment)
      throws IOException
  {
    if (!constraints.canResize(attachment)) {
      throw new UnsupportedOperationException("Cannot resize this content type");
    }

    try {
      // XXX - This is loading everything into memory! We want the send path to be stream-like.
      BitmapUtil.ScaleResult scaleResult = BitmapUtil.createScaledBytes(context, new DecryptableStreamUriLoader.DecryptableUri(attachment.getDataUri()), constraints);
      return new MediaStream(new ByteArrayInputStream(scaleResult.getBitmap()), MediaUtil.IMAGE_JPEG, scaleResult.getWidth(), scaleResult.getHeight());
    } catch (BitmapDecodingException e) {
      throw new IOException(e);
    }
  }

  public interface ProgressListener {

    void onProgress(long progress, long total);
  }
}
