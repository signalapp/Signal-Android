package org.thoughtcrime.securesms.giph.mp4;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Lifecycle;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;

import org.signal.glide.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.CornerMask;
import org.thoughtcrime.securesms.util.Projection;

import java.util.ArrayList;
import java.util.List;

/**
 * Object which holds on to an injected video player.
 */
public final class GiphyMp4ProjectionPlayerHolder implements Player.EventListener {
  private final FrameLayout         container;
  private final GiphyMp4VideoPlayer player;

  private Runnable                       onPlaybackReady;
  private MediaSource                    mediaSource;
  private GiphyMp4PlaybackPolicyEnforcer policyEnforcer;

  private GiphyMp4ProjectionPlayerHolder(@NonNull FrameLayout container, @NonNull GiphyMp4VideoPlayer player) {
    this.container = container;
    this.player    = player;
  }

  @NonNull FrameLayout getContainer() {
    return container;
  }

  public void playContent(@NonNull MediaSource mediaSource, @Nullable GiphyMp4PlaybackPolicyEnforcer policyEnforcer) {
    this.mediaSource    = mediaSource;
    this.policyEnforcer = policyEnforcer;

    player.setVideoSource(mediaSource);
    player.play();
  }

  public void clearMedia() {
    this.mediaSource    = null;
    this.policyEnforcer = null;
    player.stop();
  }

  public @Nullable MediaSource getMediaSource() {
    return mediaSource;
  }

  public void setOnPlaybackReady(@Nullable Runnable onPlaybackReady) {
    this.onPlaybackReady = onPlaybackReady;
  }

  public void hide() {
    container.setVisibility(View.GONE);
  }

  public void show() {
    container.setVisibility(View.VISIBLE);
  }

  @Override
  public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
    if (playbackState == Player.STATE_READY) {
      if (onPlaybackReady != null) {
        if (policyEnforcer != null) {
          policyEnforcer.setMediaDuration(player.getDuration());
        }
        onPlaybackReady.run();
      }
    }
  }

  @Override
  public void onPositionDiscontinuity(int reason) {
    if (policyEnforcer != null && reason == Player.DISCONTINUITY_REASON_PERIOD_TRANSITION) {
      if (policyEnforcer.endPlayback()) {
        player.stop();
      }
    }
  }

  public static @NonNull List<GiphyMp4ProjectionPlayerHolder> injectVideoViews(@NonNull Context context,
                                                                               @NonNull Lifecycle lifecycle,
                                                                               @NonNull ViewGroup viewGroup,
                                                                               int nPlayers)
  {
    List<GiphyMp4ProjectionPlayerHolder> holders        = new ArrayList<>(nPlayers);
    GiphyMp4ExoPlayerProvider            playerProvider = new GiphyMp4ExoPlayerProvider(context);

    for (int i = 0; i < nPlayers; i++) {
      FrameLayout container = (FrameLayout) LayoutInflater.from(context)
                                                          .inflate(R.layout.giphy_mp4_player, viewGroup, false);
      GiphyMp4VideoPlayer            player    = container.findViewById(R.id.video_player);
      ExoPlayer                      exoPlayer = playerProvider.create();
      GiphyMp4ProjectionPlayerHolder holder    = new GiphyMp4ProjectionPlayerHolder(container, player);

      lifecycle.addObserver(player);
      player.setExoPlayer(exoPlayer);
      player.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FILL);
      exoPlayer.addListener(holder);

      holders.add(holder);
      viewGroup.addView(container);
    }

    return holders;
  }

  public void setCorners(@Nullable Projection.Corners corners) {
    player.setCorners(corners);
  }
}
