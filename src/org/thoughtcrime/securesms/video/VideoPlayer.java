/**
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
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.MediaController;
import android.widget.Toast;
import android.widget.VideoView;

import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.ui.PlaybackControlView.VisibilityListener;
import com.google.android.exoplayer2.ui.SimpleExoPlayerView;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.attachments.AttachmentServer;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.mms.PartAuthority;
import org.thoughtcrime.securesms.mms.VideoSlide;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.video.exo.AttachmentDataSourceFactory;

import java.io.IOException;

public class VideoPlayer extends FrameLayout {

  private static final String TAG = VideoPlayer.class.getName();

  @Nullable private final VideoView           videoView;
  @Nullable private final SimpleExoPlayerView exoView;

  @Nullable private       SimpleExoPlayer           exoPlayer;
  @Nullable private       AttachmentServer          attachmentServer;
  @Nullable private       Window                    window;
  @Nullable private       PlaybackControlViewCompat mediaController;

  public VideoPlayer(Context context) {
    this(context, null);
  }

  public VideoPlayer(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public VideoPlayer(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);

    inflate(context, R.layout.video_player, this);

    if (Build.VERSION.SDK_INT >= 16) {
      this.exoView   = ViewUtil.findById(this, R.id.video_view);
      this.videoView = null;
    } else {
      this.videoView = ViewUtil.findById(this, R.id.video_view);
      this.exoView   = null;
      initializeVideoViewControls(videoView);
    }
  }

  public void setVideoSource(@NonNull MasterSecret masterSecret, @NonNull VideoSlide videoSource)
      throws IOException
  {
    if (Build.VERSION.SDK_INT >= 16) setExoViewSource(masterSecret, videoSource);
    else                             setVideoViewSource(masterSecret, videoSource);
  }

  public void cleanup() {
    if (this.attachmentServer != null) {
      this.attachmentServer.stop();
    }

    if (this.exoPlayer != null) {
      this.exoPlayer.release();
    }
  }

  public void setWindow(Window window) {
    this.window = window;
  }

  private void setExoViewSource(@NonNull MasterSecret masterSecret, @NonNull VideoSlide videoSource)
      throws IOException
  {
    BandwidthMeter         bandwidthMeter             = new DefaultBandwidthMeter();
    TrackSelection.Factory videoTrackSelectionFactory = new AdaptiveTrackSelection.Factory(bandwidthMeter);
    TrackSelector          trackSelector              = new DefaultTrackSelector(videoTrackSelectionFactory);
    LoadControl            loadControl                = new DefaultLoadControl();

    exoPlayer = ExoPlayerFactory.newSimpleInstance(getContext(), trackSelector, loadControl);
    exoPlayer.addListener(new ExoPlayerListener(window));
    exoView.setPlayer(exoPlayer);

    DefaultDataSourceFactory    defaultDataSourceFactory    = new DefaultDataSourceFactory(getContext(), "GenericUserAgent", null);
    AttachmentDataSourceFactory attachmentDataSourceFactory = new AttachmentDataSourceFactory(getContext(), masterSecret, defaultDataSourceFactory, null);
    ExtractorsFactory           extractorsFactory           = new DefaultExtractorsFactory();

    MediaSource mediaSource = new ExtractorMediaSource(videoSource.getUri(), attachmentDataSourceFactory, extractorsFactory, null, null);

    exoPlayer.prepare(mediaSource);
    exoPlayer.setPlayWhenReady(true);
  }

  private void setVideoViewSource(@NonNull MasterSecret masterSecret, @NonNull VideoSlide videoSource)
    throws IOException
  {
    if (this.attachmentServer != null) {
      this.attachmentServer.stop();
    }

    if (videoSource.getUri() != null && PartAuthority.isLocalUri(videoSource.getUri())) {
      Log.w(TAG, "Starting video attachment server for part provider Uri...");
      this.attachmentServer = new AttachmentServer(getContext(), masterSecret, videoSource.asAttachment());
      this.attachmentServer.start();

      this.videoView.setVideoURI(this.attachmentServer.getUri());
    } else if (videoSource.getUri() != null) {
      Log.w(TAG, "Playing video directly from non-local Uri...");
      this.videoView.setVideoURI(videoSource.getUri());
    } else {
      Toast.makeText(getContext(), getContext().getString(R.string.VideoPlayer_error_playing_video), Toast.LENGTH_LONG).show();
      return;
    }

    this.videoView.start();
  }

  private void initializeVideoViewControls(@NonNull VideoView videoView) {
    mediaController = new PlaybackControlViewCompat(getContext());
    mediaController.setAnchorView(videoView);
    mediaController.setMediaPlayer(videoView);

    videoView.setMediaController(mediaController);
  }

  public void setPlaybackControlVisibilityListener(VisibilityListener listener) {
    if (exoView != null)         exoView.setControllerVisibilityListener(listener);
    if (mediaController != null) mediaController.setVisibilityListener(listener);
  }

  private class PlaybackControlViewCompat extends MediaController {
    private VisibilityListener visibilityListener;
    private static final int DEFAULT_TIMEOUT = 5000;

    private PlaybackControlViewCompat(Context context) {
      super(context);
    }

    @Override
    public void show() {
      show(DEFAULT_TIMEOUT);
    }

    @Override
    public void show(int timeout) {
      super.show(timeout);
      notifyVisibilityListener(View.VISIBLE);
    }

    @Override
    public void hide() {
      super.hide();
      notifyVisibilityListener(View.GONE);
    }

    private void setVisibilityListener(VisibilityListener listener) {
      this.visibilityListener = listener;
    }

    private void notifyVisibilityListener(int visibility) {
      if (visibilityListener != null) visibilityListener.onVisibilityChange(visibility);
    }
  }

  private class ExoPlayerListener implements ExoPlayer.EventListener {
    private final Window window;

    ExoPlayerListener(Window window) {
      this.window = window;
    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
      switch(playbackState) {
        case ExoPlayer.STATE_IDLE:
        case ExoPlayer.STATE_BUFFERING:
        case ExoPlayer.STATE_ENDED:
          window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
          break;
        case ExoPlayer.STATE_READY:
          if (playWhenReady) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
          } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
          }
          break;
        default:
          break;
      }
    }

    @Override
    public void onTimelineChanged(Timeline timeline, Object manifest) { }

    @Override
    public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) { }

    @Override
    public void onLoadingChanged(boolean isLoading) { }

    @Override
    public void onPlayerError(ExoPlaybackException error) { }

    @Override
    public void onPositionDiscontinuity() { }
  }
}
