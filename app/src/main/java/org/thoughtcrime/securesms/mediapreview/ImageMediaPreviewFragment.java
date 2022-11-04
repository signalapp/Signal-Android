package org.thoughtcrime.securesms.mediapreview;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.ZoomingImageView;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.thoughtcrime.securesms.util.MediaUtil;

public final class ImageMediaPreviewFragment extends MediaPreviewFragment {
  private MediaPreviewPlayerControlView bottomBarControlView;

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container,
                           Bundle savedInstanceState) {
    ZoomingImageView zoomingImageView = (ZoomingImageView) inflater.inflate(R.layout.media_preview_image_fragment, container, false);
    GlideRequests    glideRequests    = GlideApp.with(requireActivity());
    Bundle           arguments        = requireArguments();
    Uri              uri              = arguments.getParcelable(DATA_URI);
    String           contentType      = arguments.getString(DATA_CONTENT_TYPE);

    if (!MediaUtil.isImageType(contentType)) {
      throw new AssertionError("This fragment can only display images");
    }

    //noinspection ConstantConditions
    zoomingImageView.setImageUri(glideRequests, uri, contentType, () -> events.onMediaReady());

    zoomingImageView.setOnClickListener(v -> events.singleTapOnMedia());

    return zoomingImageView;
  }

  @Override
  public void cleanUp() {
    bottomBarControlView = null;
  }

  @Override
  public void pause() {}

  @Override
  public void setBottomButtonControls(MediaPreviewPlayerControlView playerControlView) {
    bottomBarControlView = playerControlView;
  }
}
