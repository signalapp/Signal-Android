package org.thoughtcrime.securesms.giph.mp4;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.util.Projection;
import org.thoughtcrime.securesms.video.exo.ExoPlayerKt;

import java.util.ArrayList;
import java.util.List;

/**
 * Object which holds on to an injected video player.
 */
public final class GiphyMp4ProjectionPlayerHolder implements Player.Listener, DefaultLifecycleObserver {
  private static final String TAG = Log.tag(GiphyMp4ProjectionPlayerHolder.class);

  private final FrameLayout         container;
  private final GiphyMp4VideoPlayer player;

  private Runnable                       onPlaybackReady;
  private MediaItem                      mediaItem;
  private GiphyMp4PlaybackPolicyEnforcer policyEnforcer;

  private GiphyMp4ProjectionPlayerHolder(@NonNull FrameLayout container, @NonNull GiphyMp4VideoPlayer player) {
    this.container = container;
    this.player    = player;
  }

  @NonNull FrameLayout getContainer() {
    return container;
  }

  public void playContent(@NonNull MediaItem mediaItem, @Nullable GiphyMp4PlaybackPolicyEnforcer policyEnforcer) {
    this.mediaItem      = mediaItem;
    this.policyEnforcer = policyEnforcer;

    if (player.getExoPlayer() == null) {
      ExoPlayer fromPool = ApplicationDependencies.getExoPlayerPool().get(TAG);

      if (fromPool == null) {
        Log.i(TAG, "Could not get exoplayer from pool.");
        return;
      } else {
        ExoPlayerKt.configureForGifPlayback(fromPool);
        fromPool.addListener(this);
      }

      player.setExoPlayer(fromPool);
    }

    player.setVideoItem(mediaItem);
    player.play();
  }

  public void clearMedia() {
    this.mediaItem      = null;
    this.policyEnforcer = null;

    ExoPlayer exoPlayer = player.getExoPlayer();
    if (exoPlayer != null) {
      player.stop();
      player.setExoPlayer(null);
      exoPlayer.removeListener(this);
      ApplicationDependencies.getExoPlayerPool().pool(exoPlayer);
    }
  }

  public @Nullable MediaItem getMediaItem() {
    return mediaItem;
  }

  public void setOnPlaybackReady(@Nullable Runnable onPlaybackReady) {
    this.onPlaybackReady = onPlaybackReady;
    if (onPlaybackReady != null && player.getPlaybackState() == Player.STATE_READY) {
      onPlaybackReady.run();
    }
  }

  public void hide() {
    container.setVisibility(View.GONE);
  }

  public boolean isVisible() {
    return container.getVisibility() == View.VISIBLE;
  }

  public void pause() {
    player.pause();
  }

  public void show() {
    container.setVisibility(View.VISIBLE);
  }

  public void resume() {
    player.play();
  }

  @Override
  public void onPlaybackStateChanged(int playbackState) {
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
  public void onPositionDiscontinuity(@NonNull Player.PositionInfo oldPosition,
                                      @NonNull Player.PositionInfo newPosition,
                                      int reason)
  {
    if (policyEnforcer != null && reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
      if (policyEnforcer.endPlayback()) {
        player.stop();
      }
    }
  }

  @Override
  public void onResume(@NonNull LifecycleOwner owner) {
    if (mediaItem != null) {
      ExoPlayer fromPool = ApplicationDependencies.getExoPlayerPool().get(TAG);
      if (fromPool != null) {
        ExoPlayerKt.configureForGifPlayback(fromPool);
        fromPool.addListener(this);
        player.setExoPlayer(fromPool);
        player.setVideoItem(mediaItem);
        player.play();
      }
    }
  }

  @Override
  public void onPause(@NonNull LifecycleOwner owner) {
    if (player.getExoPlayer() != null) {
      player.getExoPlayer().stop();
      player.getExoPlayer().clearMediaItems();
      player.getExoPlayer().removeListener(this);
      ApplicationDependencies.getExoPlayerPool().pool(player.getExoPlayer());
      player.setExoPlayer(null);
    }
  }

  public static @NonNull List<GiphyMp4ProjectionPlayerHolder> injectVideoViews(@NonNull Context context,
                                                                               @NonNull Lifecycle lifecycle,
                                                                               @NonNull ViewGroup viewGroup,
                                                                               int nPlayers)
  {
    List<GiphyMp4ProjectionPlayerHolder> holders = new ArrayList<>(nPlayers);

    for (int i = 0; i < nPlayers; i++) {
      FrameLayout container = (FrameLayout) LayoutInflater.from(context)
                                                          .inflate(R.layout.giphy_mp4_player, viewGroup, false);
      GiphyMp4VideoPlayer            player = container.findViewById(R.id.video_player);
      GiphyMp4ProjectionPlayerHolder holder = new GiphyMp4ProjectionPlayerHolder(container, player);

      lifecycle.addObserver(holder);
      player.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FILL);

      holders.add(holder);
      viewGroup.addView(container);
    }

    return holders;
  }

  public void setCorners(@Nullable Projection.Corners corners) {
    player.setCorners(corners);
  }

  public @Nullable Bitmap getBitmap() {
    return player.getBitmap();
  }
}
