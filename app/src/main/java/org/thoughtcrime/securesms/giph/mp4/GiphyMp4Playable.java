package org.thoughtcrime.securesms.giph.mp4;

import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.MediaItem;

import org.thoughtcrime.securesms.util.Projection;

public interface GiphyMp4Playable {
  /**
   * Shows the area in which a video would be projected. Called when a video will not
   * play back.
   */
  void showProjectionArea();

  /**
   * Hides the area in which a video would be projected. Called when a video is ready
   * to play back.
   */
  void hideProjectionArea();

  /**
   * @return The MediaItem to play back in the given VideoPlayer
   */
  default @Nullable MediaItem getMediaItem() {
    return null;
  }

  /**
   * A Playback policy enforcer, or null to loop forever.
   */
  default @Nullable GiphyMp4PlaybackPolicyEnforcer getPlaybackPolicyEnforcer() {
    return null;
  }

  /**
   * @return The position this item is in it's corresponding adapter
   */
  int getAdapterPosition();

  /**
   * Width, height, and (x,y) of view which video player will "project" into
   * @param viewGroup
   */
  @NonNull Projection getGiphyMp4PlayableProjection(@NonNull ViewGroup viewGroup);

  /**
   * Specifies whether the content can start playing.
   */
  boolean canPlayContent();

  /**
   * Specifies whether the projection from {@link #getGiphyMp4PlayableProjection(ViewGroup)} should
   * be used to project into a view.
   */
  boolean shouldProjectContent();
}
