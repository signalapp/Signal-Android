package org.thoughtcrime.securesms.mediapreview;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.ZoomingImageView;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.signal.core.util.concurrent.LifecycleDisposable;
import org.thoughtcrime.securesms.util.MediaUtil;

public final class ImageMediaPreviewFragment extends MediaPreviewFragment {
  private MediaPreviewPlayerControlView bottomBarControlView;

  private MediaPreviewV2ViewModel viewModel;
  private LifecycleDisposable     lifecycleDisposable;


  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container,
                           Bundle savedInstanceState)
  {
    View             view             = inflater.inflate(R.layout.media_preview_image_fragment, container, false);
    GlideRequests    glideRequests    = GlideApp.with(requireActivity());
    Bundle           arguments        = requireArguments();
    Uri              uri              = arguments.getParcelable(DATA_URI);
    String           contentType      = arguments.getString(DATA_CONTENT_TYPE);
    ZoomingImageView zoomingImageView = view.findViewById(R.id.zooming_image_view);

    viewModel           = new ViewModelProvider(requireActivity()).get(MediaPreviewV2ViewModel.class);
    lifecycleDisposable = new LifecycleDisposable();

    lifecycleDisposable.bindTo(getViewLifecycleOwner());

    if (!MediaUtil.isImageType(contentType)) {
      throw new AssertionError("This fragment can only display images");
    }

    //noinspection ConstantConditions
    zoomingImageView.setImageUri(glideRequests, uri, contentType, () -> events.onMediaReady());

    zoomingImageView.setOnClickListener(v -> events.singleTapOnMedia());

    lifecycleDisposable.add(viewModel.getState().distinctUntilChanged().subscribe(state -> {
      zoomingImageView.setAlpha(state.isInSharedAnimation() ? 0f : 1f);
    }));

    return view;
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
