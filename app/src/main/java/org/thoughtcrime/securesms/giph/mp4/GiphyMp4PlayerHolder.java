package org.thoughtcrime.securesms.giph.mp4;

import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.MediaSource;

/**
 * Object which holds on to an injected video player.
 */
final class GiphyMp4PlayerHolder implements Player.EventListener {
  private final FrameLayout         container;
  private final GiphyMp4VideoPlayer player;

  private Runnable    onPlaybackReady;
  private MediaSource mediaSource;

  GiphyMp4PlayerHolder(@NonNull FrameLayout container, @NonNull GiphyMp4VideoPlayer player) {
    this.container = container;
    this.player    = player;
  }

  @NonNull FrameLayout getContainer() {
    return container;
  }

  public void setMediaSource(@Nullable MediaSource mediaSource) {
    this.mediaSource = mediaSource;

    if (mediaSource != null) {
      player.setVideoSource(mediaSource);
      player.play();
    } else {
      player.stop();
    }
  }

  public @Nullable MediaSource getMediaSource() {
    return mediaSource;
  }

  void setOnPlaybackReady(@Nullable Runnable onPlaybackReady) {
    this.onPlaybackReady = onPlaybackReady;
  }

  @Override
  public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
    if (playbackState == Player.STATE_READY) {
      if (onPlaybackReady != null) {
        onPlaybackReady.run();
      }
    }
  }
}
