package org.thoughtcrime.securesms.mediapreview;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.attachments.AttachmentId;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.mms.PartUriParser;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.signal.core.util.concurrent.SimpleTask;

import java.util.Objects;

public abstract class MediaPreviewFragment extends Fragment {

  public static final String DATA_URI = "DATA_URI";

  static final String DATA_SIZE         = "DATA_SIZE";
  static final String DATA_CONTENT_TYPE = "DATA_CONTENT_TYPE";
  static final String AUTO_PLAY         = "AUTO_PLAY";
  static final String VIDEO_GIF         = "VIDEO_GIF";

  private   AttachmentId attachmentId;
  protected Events       events;

  public static MediaPreviewFragment newInstance(@NonNull Attachment attachment, boolean autoPlay) {
    return newInstance(attachment.getUri(), attachment.getContentType(), attachment.getSize(), autoPlay, attachment.isVideoGif());
  }

  public static MediaPreviewFragment newInstance(@NonNull Uri dataUri, @NonNull String contentType, long size, boolean autoPlay, boolean isVideoGif) {
    Bundle args = new Bundle();

    args.putParcelable(MediaPreviewFragment.DATA_URI, dataUri);
    args.putString(MediaPreviewFragment.DATA_CONTENT_TYPE, contentType);
    args.putLong(MediaPreviewFragment.DATA_SIZE, size);
    args.putBoolean(MediaPreviewFragment.AUTO_PLAY, autoPlay);
    args.putBoolean(MediaPreviewFragment.VIDEO_GIF, isVideoGif);

    MediaPreviewFragment fragment = createCorrectFragmentType(contentType);

    fragment.setArguments(args);

    return fragment;
  }

  private static MediaPreviewFragment createCorrectFragmentType(@NonNull String contentType) {
    if (MediaUtil.isVideo(contentType)) {
      return new VideoMediaPreviewFragment();
    } else if (MediaUtil.isImageType(contentType)) {
      return new ImageMediaPreviewFragment();
    } else {
      throw new AssertionError("Unexpected media type: " + contentType);
    }
  }

  @Override
  public void onAttach(@NonNull Context context) {
    super.onAttach(context);

    if (context instanceof Events) {
      events = (Events) context;
    } else if (getParentFragment() instanceof Events) {
      events = (Events) getParentFragment();
    } else {
      throw new AssertionError("Parent component must support " + Events.class);
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    checkMediaStillAvailable();
  }

  public abstract void cleanUp();
  public abstract void pause();
  public abstract void setBottomButtonControls(MediaPreviewPlayerControlView playerControlView);

  private void checkMediaStillAvailable() {
    if (attachmentId == null) {
      attachmentId = new PartUriParser(Objects.requireNonNull(requireArguments().getParcelable(DATA_URI))).getPartId();
    }

    SimpleTask.run(getViewLifecycleOwner().getLifecycle(),
                   () -> SignalDatabase.attachments().hasAttachment(attachmentId),
                   hasAttachment -> { if (!hasAttachment) events.onMediaNotAvailable(); });
  }

  public interface Events {
    boolean singleTapOnMedia();
    void onMediaNotAvailable();
    void onMediaReady();
    void onPlaying();
    void onStopped();
    default @Nullable VideoControlsDelegate getVideoControlsDelegate() {
      return null;
    }
  }
}
