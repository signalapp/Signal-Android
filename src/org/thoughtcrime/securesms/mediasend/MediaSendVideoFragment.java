package org.thoughtcrime.securesms.mediasend;

import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.mms.VideoSlide;
import org.thoughtcrime.securesms.video.VideoPlayer;

import java.io.IOException;

public class MediaSendVideoFragment extends Fragment implements MediaSendPageFragment {

  private static final String TAG = MediaSendVideoFragment.class.getSimpleName();

  private static final String KEY_URI = "uri";

  private Uri uri;

  public static MediaSendVideoFragment newInstance(@NonNull Uri uri) {
    Bundle args = new Bundle();
    args.putParcelable(KEY_URI, uri);

    MediaSendVideoFragment fragment = new MediaSendVideoFragment();
    fragment.setArguments(args);
    fragment.setUri(uri);
    return fragment;
  }

  @Override
  public @Nullable View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.mediasend_video_fragment, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    uri = getArguments().getParcelable(KEY_URI);
    VideoSlide slide = new VideoSlide(requireContext(), uri, 0);
    try {
      ((VideoPlayer) view).setWindow(requireActivity().getWindow());
      ((VideoPlayer) view).setVideoSource(slide, false);
    } catch (IOException e) {
      Log.w(TAG, "Failed to play video.", e);
    }
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();

    if (getView() != null) {
      ((VideoPlayer) getView()).cleanup();
    }
  }

  @Override
  public void setUri(@NonNull Uri uri) {
    this.uri = uri;
  }

  @Override
  public @NonNull Uri getUri() {
    return uri;
  }

  @Override
  public @Nullable View getPlaybackControls() {
    VideoPlayer player = (VideoPlayer) getView();
    return player != null ? player.getControlView() : null;
  }

  @Override
  public @Nullable Object saveState() {
    return null;
  }

  @Override
  public void restoreState(@NonNull Object state) { }
}
