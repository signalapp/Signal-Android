package org.thoughtcrime.securesms.mediapreview;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.util.MediaUtil;

public abstract class MediaPreviewFragment extends Fragment {

  static final String DATA_URI          = "DATA_URI";
  static final String DATA_SIZE         = "DATA_SIZE";
  static final String DATA_CONTENT_TYPE = "DATA_CONTENT_TYPE";
  static final String AUTO_PLAY         = "AUTO_PLAY";

  protected Events events;

  public static MediaPreviewFragment newInstance(@NonNull Attachment attachment, boolean autoPlay) {
    return newInstance(attachment.getDataUri(), attachment.getContentType(), attachment.getSize(), autoPlay);
  }

  public static MediaPreviewFragment newInstance(@NonNull Uri dataUri, @NonNull String contentType, long size, boolean autoPlay) {
    Bundle args = new Bundle();

    args.putParcelable(MediaPreviewFragment.DATA_URI, dataUri);
    args.putString(MediaPreviewFragment.DATA_CONTENT_TYPE, contentType);
    args.putLong(MediaPreviewFragment.DATA_SIZE, size);
    args.putBoolean(MediaPreviewFragment.AUTO_PLAY, autoPlay);

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
    if (!(context instanceof Events)) {
      throw new AssertionError("Activity must support " + Events.class);
    }

    events = (Events) context;
  }

  public void cleanUp() {
  }

  public void pause() {
  }

  public @Nullable View getPlaybackControls() {
    return null;
  }

  public interface Events {

    boolean singleTapOnMedia();
  }
}
