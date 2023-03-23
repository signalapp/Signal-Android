package org.thoughtcrime.securesms.mediasend;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.annimon.stream.Stream;

import org.signal.core.util.ThreadUtil;
import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.attachments.AttachmentId;
import org.thoughtcrime.securesms.database.AttachmentTable;
import org.thoughtcrime.securesms.database.AttachmentTable.TransformProperties;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.JobManager;
import org.thoughtcrime.securesms.mms.GifSlide;
import org.thoughtcrime.securesms.mms.ImageSlide;
import org.thoughtcrime.securesms.mms.TextSlide;
import org.thoughtcrime.securesms.mms.VideoSlide;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.sms.MessageSender;
import org.thoughtcrime.securesms.sms.MessageSender.PreUploadResult;
import org.thoughtcrime.securesms.util.MediaUtil;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Manages the proactive upload of media during the selection process. Upload/cancel operations
 * need to be serialized, because they're asynchronous operations that depend on ordered completion.
 *
 * For example, if we begin upload of a {@link Media) but then immediately cancel it (before it was
 * enqueued on the {@link JobManager}), we need to wait until we have the jobId to cancel. This
 * class manages everything by using a single thread executor.
 *
 * This also means that unlike most repositories, the class itself is stateful. Keep that in mind
 * when using it.
 */
public class MediaUploadRepository {

  private static final String TAG = Log.tag(MediaUploadRepository.class);

  private final Context                               context;
  private final LinkedHashMap<Media, PreUploadResult> uploadResults;
  private final Executor                              executor;

  public MediaUploadRepository(@NonNull Context context) {
    this.context       = context;
    this.uploadResults = new LinkedHashMap<>();
    this.executor      = SignalExecutors.newCachedSingleThreadExecutor("signal-MediaUpload", ThreadUtil.PRIORITY_IMPORTANT_BACKGROUND_THREAD);
  }

  public void startUpload(@NonNull Media media, @Nullable Recipient recipient) {
    executor.execute(() -> uploadMediaInternal(media, recipient));
  }

  public void startUpload(@NonNull Collection<Media> mediaItems, @Nullable Recipient recipient) {
    executor.execute(() -> {
      for (Media media : mediaItems) {
        cancelUploadInternal(media);
        uploadMediaInternal(media, recipient);
      }
    });
  }

  /**
   * Given a map of old->new, cancel medias that were changed and upload their replacements. Will
   * also upload any media in the map that wasn't yet uploaded.
   */
  public void applyMediaUpdates(@NonNull Map<Media, Media> oldToNew, @Nullable Recipient recipient) {
    executor.execute(() -> {
      for (Map.Entry<Media, Media> entry : oldToNew.entrySet()) {
        Media   oldMedia = entry.getKey();
        Media   newMedia = entry.getValue();
        boolean same     = oldMedia.equals(newMedia) && hasSameTransformProperties(oldMedia, newMedia);

        if (!same || !uploadResults.containsKey(newMedia)) {
          cancelUploadInternal(oldMedia);
          uploadMediaInternal(newMedia, recipient);
        }
      }
    });
  }

  private boolean hasSameTransformProperties(@NonNull Media oldMedia, @NonNull Media newMedia) {
    TransformProperties oldProperties = oldMedia.getTransformProperties().orElse(null);
    TransformProperties newProperties = newMedia.getTransformProperties().orElse(null);

    if (oldProperties == null || newProperties == null) {
      return oldProperties == newProperties;
    }

    return !newProperties.isVideoEdited() && oldProperties.getSentMediaQuality() == newProperties.getSentMediaQuality();
  }

  public void cancelUpload(@NonNull Media media) {
    executor.execute(() -> cancelUploadInternal(media));
  }

  public void cancelUpload(@NonNull Collection<Media> mediaItems) {
    executor.execute(() -> {
      for (Media media : mediaItems) {
        cancelUploadInternal(media);
      }
    });
  }

  public void cancelAllUploads() {
    executor.execute(() -> {
      for (Media media : new HashSet<>(uploadResults.keySet())) {
        cancelUploadInternal(media);
      }
    });
  }

  public void getPreUploadResults(@NonNull Callback<Collection<PreUploadResult>> callback) {
    executor.execute(() -> callback.onResult(uploadResults.values()));
  }

  public void updateCaptions(@NonNull List<Media> updatedMedia) {
    executor.execute(() -> updateCaptionsInternal(updatedMedia));
  }

  public void updateDisplayOrder(@NonNull List<Media> mediaInOrder) {
    executor.execute(() -> updateDisplayOrderInternal(mediaInOrder));
  }

  public void deleteAbandonedAttachments() {
    executor.execute(() -> {
      int deleted = SignalDatabase.attachments().deleteAbandonedPreuploadedAttachments();
      Log.i(TAG, "Deleted " + deleted + " abandoned attachments.");
    });
  }

  @WorkerThread
  private void uploadMediaInternal(@NonNull Media media, @Nullable Recipient recipient) {
    Attachment      attachment = asAttachment(context, media);
    PreUploadResult result     = MessageSender.preUploadPushAttachment(context, attachment, recipient, media);

    if (result != null) {
      uploadResults.put(media, result);
    } else {
      Log.w(TAG, "Failed to upload media with URI: " + media.getUri());
    }
  }

  private void cancelUploadInternal(@NonNull Media media) {
    JobManager      jobManager = ApplicationDependencies.getJobManager();
    PreUploadResult result     = uploadResults.get(media);

    if (result != null) {
      Stream.of(result.getJobIds()).forEach(jobManager::cancel);
      uploadResults.remove(media);
    }
  }

  @WorkerThread
  private void updateCaptionsInternal(@NonNull List<Media> updatedMedia) {
    AttachmentTable db = SignalDatabase.attachments();

    for (Media updated : updatedMedia) {
      PreUploadResult result = uploadResults.get(updated);

      if (result != null) {
        db.updateAttachmentCaption(result.getAttachmentId(), updated.getCaption().orElse(null));
      } else {
        Log.w(TAG,"When updating captions, no pre-upload result could be found for media with URI: " + updated.getUri());
      }
    }
  }

  @WorkerThread
  private void updateDisplayOrderInternal(@NonNull List<Media> mediaInOrder) {
    Map<AttachmentId, Integer>  orderMap             = new HashMap<>();
    Map<Media, PreUploadResult> orderedUploadResults = new LinkedHashMap<>();

    for (int i = 0; i < mediaInOrder.size(); i++) {
      Media           media  = mediaInOrder.get(i);
      PreUploadResult result = uploadResults.get(media);

      if (result != null) {
        orderMap.put(result.getAttachmentId(), i);
        orderedUploadResults.put(media, result);
      } else {
        Log.w(TAG, "When updating display order, no pre-upload result could be found for media with URI: " + media.getUri());
      }
    }

    SignalDatabase.attachments().updateDisplayOrder(orderMap);

    if (orderedUploadResults.size() == uploadResults.size()) {
      uploadResults.clear();
      uploadResults.putAll(orderedUploadResults);
    }
  }

  public static @NonNull Attachment asAttachment(@NonNull Context context, @NonNull Media media) {
    if (MediaUtil.isVideoType(media.getMimeType())) {
      return new VideoSlide(context, media.getUri(), media.getSize(), media.isVideoGif(), media.getWidth(), media.getHeight(), media.getCaption().orElse(null), media.getTransformProperties().orElse(null)).asAttachment();
    } else if (MediaUtil.isGif(media.getMimeType())) {
      return new GifSlide(context, media.getUri(), media.getSize(), media.getWidth(), media.getHeight(), media.isBorderless(), media.getCaption().orElse(null)).asAttachment();
    } else if (MediaUtil.isImageType(media.getMimeType())) {
      return new ImageSlide(context, media.getUri(), media.getMimeType(), media.getSize(), media.getWidth(), media.getHeight(), media.isBorderless(), media.getCaption().orElse(null), null, media.getTransformProperties().orElse(null)).asAttachment();
    } else if (MediaUtil.isTextType(media.getMimeType())) {
      return new TextSlide(context, media.getUri(), null, media.getSize()).asAttachment();
    } else {
      throw new AssertionError("Unexpected mimeType: " + media.getMimeType());
    }
  }

  public interface Callback<E> {
    void onResult(@NonNull E result);
  }
}
