package org.thoughtcrime.securesms.giph.mp4;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.DefaultLifecycleObserver;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;
import com.google.android.exoplayer2.ui.PlayerView;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.CornerMask;
import org.thoughtcrime.securesms.util.Projection;

/**
 * Video Player class specifically created for the GiphyMp4Fragment.
 */
public final class GiphyMp4VideoPlayer extends FrameLayout implements DefaultLifecycleObserver {

  @SuppressWarnings("unused")
  private static final String TAG = Log.tag(GiphyMp4VideoPlayer.class);

  private final PlayerView      exoView;
  private       SimpleExoPlayer exoPlayer;
  private       CornerMask      cornerMask;
  private       MediaItem       mediaItem;

  public GiphyMp4VideoPlayer(Context context) {
    this(context, null);
  }

  public GiphyMp4VideoPlayer(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public GiphyMp4VideoPlayer(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);

    inflate(context, R.layout.gif_player, this);

    this.exoView = findViewById(R.id.video_view);
  }

  @Override
  protected void onDetachedFromWindow() {
    Log.d(TAG, "onDetachedFromWindow");
    super.onDetachedFromWindow();
  }

  @Override
  protected void dispatchDraw(Canvas canvas) {
    super.dispatchDraw(canvas);

    if (cornerMask != null) {
      cornerMask.mask(canvas);
    }
  }

  @Nullable SimpleExoPlayer getExoPlayer() {
    return exoPlayer;
  }

  void setExoPlayer(@Nullable SimpleExoPlayer exoPlayer) {
    exoView.setPlayer(exoPlayer);
    this.exoPlayer = exoPlayer;
  }

  int getPlaybackState() {
    if (exoPlayer != null) {
      return exoPlayer.getPlaybackState();
    } else {
      return -1;
    }
  }

  void setVideoItem(@NonNull MediaItem mediaItem) {
    this.mediaItem = mediaItem;
    exoPlayer.setMediaItem(mediaItem);
    exoPlayer.prepare();
  }

  void setCorners(@Nullable Projection.Corners corners) {
    if (corners == null) {
      this.cornerMask = null;
    } else {
      this.cornerMask = new CornerMask(this);
      this.cornerMask.setRadii(corners.getTopLeft(), corners.getTopRight(), corners.getBottomRight(), corners.getBottomLeft());
    }
    invalidate();
  }
  
  void play() {
    if (exoPlayer != null) {
      exoPlayer.setPlayWhenReady(true);
    }
  }

  void stop() {
    if (exoPlayer != null) {
      exoPlayer.stop();
      exoPlayer.clearMediaItems();
      mediaItem = null;
    }
  }

  long getDuration() {
    if (exoPlayer != null) {
      return exoPlayer.getDuration();
    } else {
      return C.LENGTH_UNSET;
    }
  }

  void setResizeMode(@AspectRatioFrameLayout.ResizeMode int resizeMode) {
    exoView.setResizeMode(resizeMode);
  }
}
