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
package org.thoughtcrime.securesms.video.exo;

import android.view.Window;
import android.view.WindowManager;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayer.EventListener;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;

/**
 * Listener that manages when the screen should stay on while playing a video.
 */
public class ExoPlayerListener implements EventListener {
  private final Window window;

  public ExoPlayerListener(Window window) {
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
  public void onTimelineChanged(Timeline timeline, Object manifest) {

  }

  @Override
  public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {

  }

  @Override
  public void onLoadingChanged(boolean isLoading) {

  }

  @Override
  public void onPlayerError(ExoPlaybackException error) {

  }

  @Override
  public void onPositionDiscontinuity() {

  }
}
