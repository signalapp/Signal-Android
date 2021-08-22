package org.thoughtcrime.securesms.giph.mp4;

import android.content.Context;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;

/**
 * Provider which creates ExoPlayer instances for displaying Giphy content.
 */
final class GiphyMp4ExoPlayerProvider implements DefaultLifecycleObserver {

  private final Context context;

  GiphyMp4ExoPlayerProvider(@NonNull Context context) {
    this.context = context;
  }

  @MainThread final @NonNull ExoPlayer create() {
    SimpleExoPlayer exoPlayer = new SimpleExoPlayer.Builder(context).build();

    exoPlayer.setRepeatMode(Player.REPEAT_MODE_ALL);
    exoPlayer.setVolume(0f);

    return exoPlayer;
  }
}
