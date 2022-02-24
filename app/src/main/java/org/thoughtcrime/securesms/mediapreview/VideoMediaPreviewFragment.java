package org.thoughtcrime.securesms.mediapreview;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.mms.VideoSlide;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.video.VideoPlayer;

public final class VideoMediaPreviewFragment extends MediaPreviewFragment {

  private static final String TAG = Log.tag(VideoMediaPreviewFragment.class);

  private VideoPlayer videoView;
  private boolean     isVideoGif;

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container,
                           Bundle savedInstanceState)
  {
    View    itemView    = inflater.inflate(R.layout.media_preview_video_fragment, container, false);
    Bundle  arguments   = requireArguments();
    Uri     uri         = arguments.getParcelable(DATA_URI);
    String  contentType = arguments.getString(DATA_CONTENT_TYPE);
    long    size        = arguments.getLong(DATA_SIZE);
    boolean autoPlay    = arguments.getBoolean(AUTO_PLAY);

    isVideoGif = arguments.getBoolean(VIDEO_GIF);

    if (!MediaUtil.isVideo(contentType)) {
      throw new AssertionError("This fragment can only display video");
    }

    videoView = itemView.findViewById(R.id.video_player);

    videoView.setWindow(requireActivity().getWindow());
    videoView.setVideoSource(new VideoSlide(getContext(), uri, size, false), autoPlay);

    if (isVideoGif) {
      videoView.hideControls();
      videoView.loopForever();
    }

    videoView.setOnClickListener(v -> events.singleTapOnMedia());

    return itemView;
  }

  @Override
  public void cleanUp() {
    if (videoView != null) {
      videoView.cleanup();
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    if (videoView != null && isVideoGif) {
      videoView.play();
    }

    if (events.getVideoControlsDelegate() != null) {
      events.getVideoControlsDelegate().attachPlayer(getUri(), videoView);
    }
  }

  @Override
  public void pause() {
    if (videoView != null) {
      videoView.pause();
    }

    if (events.getVideoControlsDelegate() != null) {
      events.getVideoControlsDelegate().detachPlayer();
    }
  }

  @Override
  public View getPlaybackControls() {
    return videoView != null && !isVideoGif ? videoView.getControlView() : null;
  }

  private @NonNull Uri getUri() {
    return requireArguments().getParcelable(DATA_URI);
  }
}
