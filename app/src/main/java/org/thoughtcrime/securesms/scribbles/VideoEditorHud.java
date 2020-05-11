package org.thoughtcrime.securesms.scribbles;

import android.animation.Animator;
import android.content.Context;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.mms.VideoSlide;
import org.thoughtcrime.securesms.video.DecryptableUriVideoInput;
import org.thoughtcrime.securesms.video.videoconverter.VideoThumbnailsRangeSelectorView;

import java.io.IOException;

/**
 * The HUD (heads-up display) that contains all of the tools for editing video.
 */
public final class VideoEditorHud extends LinearLayout {

  @SuppressWarnings("unused")
  private static final String TAG = Log.tag(VideoEditorHud.class);

  private VideoThumbnailsRangeSelectorView videoTimeLine;
  private EventListener                    eventListener;
  private View                             playOverlay;

  public VideoEditorHud(@NonNull Context context) {
    super(context);
    initialize();
  }

  public VideoEditorHud(@NonNull Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    initialize();
  }

  public VideoEditorHud(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    initialize();
  }

  private void initialize() {
    View root = inflate(getContext(), R.layout.video_editor_hud, this);
    setOrientation(VERTICAL);

    videoTimeLine = root.findViewById(R.id.video_timeline);
    playOverlay   = root.findViewById(R.id.play_overlay);

    playOverlay.setOnClickListener(v -> eventListener.onPlay());
  }

  public void setEventListener(EventListener eventListener) {
    this.eventListener = eventListener;
  }

  @RequiresApi(api = 23)
  public void setVideoSource(VideoSlide slide) throws IOException {
    Uri uri = slide.getUri();

    if (uri == null || !slide.hasVideo()) {
      return;
    }

    videoTimeLine.setInput(DecryptableUriVideoInput.createForUri(getContext(), uri));

    videoTimeLine.setOnRangeChangeListener(new VideoThumbnailsRangeSelectorView.OnRangeChangeListener() {

      @Override
      public void onPositionDrag(long position) {
        if (eventListener != null) {
          eventListener.onSeek(position, false);
        }
      }

      @Override
      public void onEndPositionDrag(long position) {
        if (eventListener != null) {
          eventListener.onSeek(position, true);
        }
      }

      @Override
      public void onRangeDrag(long minValue, long maxValue, long duration, VideoThumbnailsRangeSelectorView.Thumb thumb) {
        if (eventListener != null) {
          eventListener.onEditVideoDuration(duration, minValue, maxValue, thumb == VideoThumbnailsRangeSelectorView.Thumb.MIN, false);
        }
      }

      @Override
      public void onRangeDragEnd(long minValue, long maxValue, long duration, VideoThumbnailsRangeSelectorView.Thumb thumb) {
        if (eventListener != null) {
          eventListener.onEditVideoDuration(duration, minValue, maxValue, thumb == VideoThumbnailsRangeSelectorView.Thumb.MIN, true);
        }
      }
    });
  }

  public void showPlayButton() {
    playOverlay.setVisibility(VISIBLE);
    playOverlay.animate()
      .setListener(null)
      .alpha(1)
      .scaleX(1).scaleY(1)
      .setInterpolator(new OvershootInterpolator())
      .start();
  }

  public void fadePlayButton() {
    playOverlay.animate()
      .setListener(new Animator.AnimatorListener() {
        @Override
        public void onAnimationEnd(Animator animation) {
          playOverlay.setVisibility(GONE);
        }

        @Override
        public void onAnimationStart(Animator animation) {}

        @Override
        public void onAnimationCancel(Animator animation) {}

        @Override
        public void onAnimationRepeat(Animator animation) {}
      })
      .alpha(0)
      .scaleX(0.8f).scaleY(0.8f)
      .start();
  }

  public void hidePlayButton() {
    playOverlay.setVisibility(GONE);
    playOverlay.setAlpha(0);
    playOverlay.setScaleX(0.8f);
    playOverlay.setScaleY(0.8f);
  }

  @RequiresApi(api = 23)
  public void setDurationRange(long totalDuration, long fromDuration, long toDuration) {
    videoTimeLine.setRange(fromDuration, toDuration);
  }

  @RequiresApi(api = 23)
  public void setPosition(long playbackPositionUs) {
    videoTimeLine.setActualPosition(playbackPositionUs);
  }

  public interface EventListener {

    void onEditVideoDuration(long totalDurationUs, long startTimeUs, long endTimeUs, boolean fromEdited, boolean editingComplete);

    void onPlay();

    void onSeek(long position, boolean dragComplete);
  }
}
