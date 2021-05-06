package org.thoughtcrime.securesms.giph.mp4;

import android.content.Context;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;

import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelector;

/**
 * Provider which creates ExoPlayer instances for displaying Giphy content.
 */
final class GiphyMp4ExoPlayerProvider implements DefaultLifecycleObserver {

  private final Context                  context;
  private final TrackSelection.Factory   videoTrackSelectionFactory;
  private final DefaultRenderersFactory  renderersFactory;
  private final TrackSelector            trackSelector;
  private final LoadControl              loadControl;

  GiphyMp4ExoPlayerProvider(@NonNull Context context) {
    this.context                    = context;
    this.videoTrackSelectionFactory = new AdaptiveTrackSelection.Factory();
    this.renderersFactory           = new DefaultRenderersFactory(context);
    this.trackSelector              = new DefaultTrackSelector(videoTrackSelectionFactory);
    this.loadControl                = new DefaultLoadControl();
  }

  @MainThread final @NonNull ExoPlayer create() {
    SimpleExoPlayer exoPlayer = ExoPlayerFactory.newSimpleInstance(context, renderersFactory, trackSelector, loadControl);

    exoPlayer.setRepeatMode(Player.REPEAT_MODE_ALL);
    exoPlayer.setVolume(0f);

    return exoPlayer;
  }
}
