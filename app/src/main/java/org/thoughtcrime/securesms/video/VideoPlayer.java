/*
 * Copyright (C) 2017 Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms.video;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.source.ClippingMediaSource;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.ui.PlayerControlView;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.mms.VideoSlide;
import org.thoughtcrime.securesms.video.exo.AttachmentDataSourceFactory;

import java.util.concurrent.TimeUnit;

public class VideoPlayer extends FrameLayout {

  @SuppressWarnings("unused")
  private static final String TAG = Log.tag(VideoPlayer.class);

  private final PlayerView        exoView;
  private final PlayerControlView exoControls;

  private SimpleExoPlayer     exoPlayer;
  private Window              window;
  private PlayerStateCallback playerStateCallback;
  private PlayerCallback      playerCallback;
  private boolean             clipped;
  private long                clippedStartUs;

  public VideoPlayer(Context context) {
    this(context, null);
  }

  public VideoPlayer(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public VideoPlayer(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);

    inflate(context, R.layout.video_player, this);

    this.exoView     = findViewById(R.id.video_view);
    this.exoControls = new PlayerControlView(getContext());
    this.exoControls.setShowTimeoutMs(-1);
  }

  private CreateMediaSource createMediaSource;

  public void setVideoSource(@NonNull VideoSlide videoSource, boolean autoplay) {
    Context                 context                    = getContext();
    DefaultRenderersFactory renderersFactory           = new DefaultRenderersFactory(context);
    TrackSelection.Factory  videoTrackSelectionFactory = new AdaptiveTrackSelection.Factory();
    TrackSelector           trackSelector              = new DefaultTrackSelector(videoTrackSelectionFactory);
    LoadControl             loadControl                = new DefaultLoadControl();

    exoPlayer = ExoPlayerFactory.newSimpleInstance(context, renderersFactory, trackSelector, loadControl);
    exoPlayer.addListener(new ExoPlayerListener(window, playerStateCallback));
    exoPlayer.addListener(new Player.DefaultEventListener() {
      @Override
      public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        if (playerCallback != null) {
          switch (playbackState) {
            case Player.STATE_READY:
              if (playWhenReady) playerCallback.onPlaying();
              break;
            case Player.STATE_ENDED:
              playerCallback.onStopped();
              break;
          }
        }
      }
    });
    exoView.setPlayer(exoPlayer);
    exoControls.setPlayer(exoPlayer);

    DefaultDataSourceFactory    defaultDataSourceFactory    = new DefaultDataSourceFactory(context, "GenericUserAgent", null);
    AttachmentDataSourceFactory attachmentDataSourceFactory = new AttachmentDataSourceFactory(context, defaultDataSourceFactory, null);
    ExtractorsFactory           extractorsFactory           = new DefaultExtractorsFactory();

    createMediaSource = () -> new ExtractorMediaSource.Factory(attachmentDataSourceFactory)
                                                      .setExtractorsFactory(extractorsFactory)
                                                      .createMediaSource(videoSource.getUri());

    exoPlayer.prepare(createMediaSource.create());
    exoPlayer.setPlayWhenReady(autoplay);
  }

  public void pause() {
    this.exoPlayer.setPlayWhenReady(false);
  }

  public void hideControls() {
    if (this.exoView != null) {
      this.exoView.hideController();
    }
  }

  public @Nullable View getControlView() {
    if (this.exoControls != null) {
      return this.exoControls;
    }
    return null;
  }

  public void cleanup() {
    if (this.exoPlayer != null) {
      this.exoPlayer.release();
    }
  }

  public void loopForever() {
    if (this.exoPlayer != null) {
      exoPlayer.setRepeatMode(Player.REPEAT_MODE_ONE);
    }
  }

  public long getDuration() {
    if (this.exoPlayer != null) {
      return this.exoPlayer.getDuration();
    }
    return 0L;
  }

  public long getPlaybackPosition() {
    if (this.exoPlayer != null) {
      return this.exoPlayer.getCurrentPosition();
    }
    return 0L;
  }

  public long getPlaybackPositionUs() {
    if (this.exoPlayer != null) {
      return TimeUnit.MILLISECONDS.toMicros(this.exoPlayer.getCurrentPosition()) + clippedStartUs;
    }
    return 0L;
  }

  public void setPlaybackPosition(long positionMs) {
    if (this.exoPlayer != null) {
      this.exoPlayer.seekTo(positionMs);
    }
  }

  public void clip(long fromUs, long toUs, boolean playWhenReady) {
    if (this.exoPlayer != null && createMediaSource != null) {
      MediaSource clippedMediaSource = new ClippingMediaSource(createMediaSource.create(), fromUs, toUs);
      exoPlayer.prepare(clippedMediaSource);
      exoPlayer.setPlayWhenReady(playWhenReady);
      clipped        = true;
      clippedStartUs = fromUs;
    }
  }

  public void removeClip(boolean playWhenReady) {
    if (exoPlayer != null && createMediaSource != null) {
      if (clipped) {
        exoPlayer.prepare(createMediaSource.create());
        clipped = false;
        clippedStartUs = 0;
      }
      exoPlayer.setPlayWhenReady(playWhenReady);
    }
  }

  public void setWindow(@Nullable Window window) {
    this.window = window;
  }

  public void setPlayerStateCallbacks(@Nullable PlayerStateCallback playerStateCallback) {
    this.playerStateCallback = playerStateCallback;
  }

  public void setPlayerCallback(PlayerCallback playerCallback) {
    this.playerCallback = playerCallback;
  }

  /**
   * Resumes a paused video, or restarts if at end of video.
   */
  public void play() {
    if (exoPlayer != null) {
      exoPlayer.setPlayWhenReady(true);
      if (exoPlayer.getCurrentPosition() >= exoPlayer.getDuration()) {
        exoPlayer.seekTo(0);
      }
    }
  }

  private static class ExoPlayerListener extends Player.DefaultEventListener {
    private final Window              window;
    private final PlayerStateCallback playerStateCallback;

    ExoPlayerListener(Window window, PlayerStateCallback playerStateCallback) {
      this.window              = window;
      this.playerStateCallback = playerStateCallback;
    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
      switch(playbackState) {
        case Player.STATE_IDLE:
        case Player.STATE_BUFFERING:
        case Player.STATE_ENDED:
          window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
          break;
        case Player.STATE_READY:
          if (playWhenReady) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
          } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
          }
          notifyPlayerReady();
          break;
        default:
          break;
      }
    }

    private void notifyPlayerReady() {
      if (playerStateCallback != null) playerStateCallback.onPlayerReady();
    }
  }

  public interface PlayerStateCallback {
    void onPlayerReady();
  }

  public interface PlayerCallback {

    void onPlaying();

    void onStopped();
  }

  private interface CreateMediaSource {
    MediaSource create();
  }
}
