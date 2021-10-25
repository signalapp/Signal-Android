package org.thoughtcrime.securesms.mediasend;

import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.mms.MediaConstraints;
import org.thoughtcrime.securesms.mms.VideoSlide;
import org.thoughtcrime.securesms.scribbles.VideoEditorHud;
import org.thoughtcrime.securesms.util.Throttler;
import org.thoughtcrime.securesms.video.VideoBitRateCalculator;
import org.thoughtcrime.securesms.video.VideoPlayer;

import java.io.IOException;

public class VideoEditorFragment extends Fragment implements VideoEditorHud.EventListener,
                                                             MediaSendPageFragment {

  private static final String TAG = Log.tag(VideoEditorFragment.class);

  private static final String KEY_URI          = "uri";
  private static final String KEY_MAX_OUTPUT   = "max_output_size";
  private static final String KEY_MAX_SEND     = "max_send_size";
  private static final String KEY_IS_VIDEO_GIF = "is_video_gif";

  private final Throttler videoScanThrottle = new Throttler(150);
  private final Handler   handler           = new Handler(Looper.getMainLooper());

            private Controller     controller;
            private Data           data           = new Data();
            private Uri            uri;
            private boolean        isVideoGif;
            private VideoPlayer    player;
  @Nullable private VideoEditorHud hud;
            private Runnable       updatePosition;
            private boolean        isInEdit;
            private boolean        wasPlayingBeforeEdit;

  public static VideoEditorFragment newInstance(@NonNull Uri uri, long maxCompressedVideoSize, long maxAttachmentSize, boolean isVideoGif) {
    Bundle args = new Bundle();
    args.putParcelable(KEY_URI, uri);
    args.putLong(KEY_MAX_OUTPUT, maxCompressedVideoSize);
    args.putLong(KEY_MAX_SEND, maxAttachmentSize);
    args.putBoolean(KEY_IS_VIDEO_GIF, isVideoGif);

    VideoEditorFragment fragment = new VideoEditorFragment();
    fragment.setArguments(args);
    fragment.setUri(uri);
    return fragment;
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    if (getActivity() instanceof Controller) {
      controller = (Controller) getActivity();
    } else if (getParentFragment() instanceof Controller) {
      controller = (Controller) getParentFragment();
    } else {
      throw new IllegalStateException("Parent must implement Controller interface.");
    }
  }

  @Override
  public @Nullable View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.mediasend_video_fragment, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    player = view.findViewById(R.id.video_player);

    uri        = requireArguments().getParcelable(KEY_URI);
    isVideoGif = requireArguments().getBoolean(KEY_IS_VIDEO_GIF);

    long       maxOutput  = requireArguments().getLong(KEY_MAX_OUTPUT);
    long       maxSend    = requireArguments().getLong(KEY_MAX_SEND);
    VideoSlide slide      = new VideoSlide(requireContext(), uri, 0, isVideoGif);
    boolean    autoplay   = isVideoGif;

    player.setWindow(requireActivity().getWindow());
    player.setVideoSource(slide, autoplay);

    if (slide.isVideoGif()) {
      player.setPlayerCallback(new VideoPlayer.PlayerCallback() {
        @Override
        public void onPlaying() {
          controller.onPlayerReady();
        }

        @Override
        public void onStopped() {
          // Do nothing.
        }

        @Override
        public void onError() {
          controller.onPlayerError();
        }
      });
      player.hideControls();
      player.loopForever();
    } else if (MediaConstraints.isVideoTranscodeAvailable()) {
      hud = view.findViewById(R.id.video_editor_hud);
      hud.setEventListener(this);
      updateHud(data);
      if (data.durationEdited) {
        player.clip(data.startTimeUs, data.endTimeUs, autoplay);
      }
      try {
        hud.setVideoSource(slide, new VideoBitRateCalculator(maxOutput), maxSend);
        hud.setVisibility(View.VISIBLE);
        startPositionUpdates();
      } catch (IOException e) {
        Log.w(TAG, e);
      }

      player.setOnClickListener(v -> {
        player.pause();
        hud.showPlayButton();
      });

      player.setPlayerCallback(new VideoPlayer.PlayerCallback() {

        @Override
        public void onReady() {
          controller.onPlayerReady();
        }

        @Override
        public void onPlaying() {
          hud.fadePlayButton();
        }

        @Override
        public void onStopped() {
          hud.showPlayButton();
        }

        @Override
        public void onError() {
          controller.onPlayerError();
        }
      });
    }
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();

    if (player != null) {
      player.cleanup();
    }
  }

  @Override
  public void onPause() {
    super.onPause();
    notifyHidden();

    stopPositionUpdates();
  }

  @Override
  public void onResume() {
    super.onResume();
    startPositionUpdates();

    if (player != null && isVideoGif) {
      player.play();
    }
  }

  private void startPositionUpdates() {
    if (hud != null && Build.VERSION.SDK_INT >= 23) {
      stopPositionUpdates();
      updatePosition = new Runnable() {
        @Override
        public void run() {
          hud.setPosition(player.getPlaybackPositionUs());
          handler.postDelayed(this, 100);
        }
      };
      handler.post(updatePosition);
    }
  }

  private void stopPositionUpdates() {
    handler.removeCallbacks(updatePosition);
  }

  @Override
  public void onHiddenChanged(boolean hidden) {
    if (hidden) {
      notifyHidden();
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
    if (hud != null && hud.getVisibility() == View.VISIBLE) return null;
    else if (isVideoGif)                                    return null;
    else if (player != null)                                return player.getControlView();
    else                                                    return null;
  }

  @Override
  public @Nullable Object saveState() {
    return data;
  }

  @Override
  public void restoreState(@NonNull Object state) {
    if (state instanceof Data) {
      data = (Data) state;
      if (Build.VERSION.SDK_INT >= 23) {
        updateHud(data);
      }
    } else {
      Log.w(TAG, "Received a bad saved state. Received class: " + state.getClass().getName());
    }
  }

  @RequiresApi(api = 23)
  private void updateHud(Data data) {
    if (hud != null && data.totalDurationUs > 0 && data.durationEdited) {
      hud.setDurationRange(data.totalDurationUs, data.startTimeUs, data.endTimeUs);
    }
  }

  @Override
  public void notifyHidden() {
    pausePlayback();
  }

  public void pausePlayback() {
    if (player != null) {
      player.pause();
      if (hud != null) {
        hud.showPlayButton();
      }
    }
  }

  @Override
  public void onEditVideoDuration(long totalDurationUs, long startTimeUs, long endTimeUs, boolean fromEdited, boolean editingComplete) {
    controller.onTouchEventsNeeded(!editingComplete);

    if (hud != null) {
      hud.hidePlayButton();
    }

    final long clampedStartTime = Math.max(startTimeUs, 0);

    boolean wasEdited      = data.durationEdited;
    boolean durationEdited = clampedStartTime > 0 || endTimeUs < totalDurationUs;

    data.durationEdited  = durationEdited;
    data.totalDurationUs = totalDurationUs;
    data.startTimeUs     = clampedStartTime;
    data.endTimeUs       = endTimeUs;

    if (editingComplete) {
      isInEdit = false;
      videoScanThrottle.clear();
    } else if (!isInEdit) {
      isInEdit = true;
      wasPlayingBeforeEdit = player.isPlaying();
    }

    videoScanThrottle.publish(() -> {
      player.pause();
      if (!editingComplete) {
        player.removeClip(false);
      }
      player.setPlaybackPosition(fromEdited || editingComplete ? clampedStartTime / 1000 : endTimeUs / 1000);
      if (editingComplete) {
        if (durationEdited) {
          player.clip(clampedStartTime, endTimeUs, wasPlayingBeforeEdit);
        } else {
          player.removeClip(wasPlayingBeforeEdit);
        }

        if (!wasPlayingBeforeEdit) {
          hud.showPlayButton();
        }
      }
    });

    if (!wasEdited && durationEdited) {
      controller.onVideoBeginEdit(uri);
    }

    if (editingComplete) {
      controller.onVideoEndEdit(uri);
    }
  }

  @Override
  public void onPlay() {
    player.play();
  }

  @Override
  public void onSeek(long position, boolean dragComplete) {
    if (dragComplete) {
      videoScanThrottle.clear();
    }

    videoScanThrottle.publish(() -> {
      player.pause();
      player.setPlaybackPosition(position);
    });
  }

  public static class Data {
    boolean durationEdited;
    long    totalDurationUs;
    long    startTimeUs;
    long    endTimeUs;

    public boolean isDurationEdited() {
      return durationEdited;
    }

    public @NonNull Bundle getBundle() {
      Bundle bundle = new Bundle();
      bundle.putByte("EDITED", (byte) (durationEdited ? 1 : 0));
      bundle.putLong("TOTAL", totalDurationUs);
      bundle.putLong("START", startTimeUs);
      bundle.putLong("END", endTimeUs);

      return bundle;
    }

    public static @NonNull Data fromBundle(@NonNull Bundle bundle) {
      Data data = new Data();
      data.durationEdited = bundle.getByte("EDITED") == (byte) 1;
      data.totalDurationUs = bundle.getLong("TOTAL");
      data.startTimeUs = bundle.getLong("START");
      data.endTimeUs = bundle.getLong("END");

      return data;
    }
  }

  public interface Controller {

    void onPlayerReady();

    void onPlayerError();

    void onTouchEventsNeeded(boolean needed);

    void onVideoBeginEdit(@NonNull Uri uri);

    void onVideoEndEdit(@NonNull Uri uri);
  }
}
