package org.thoughtcrime.securesms.conversation;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.components.Outliner;
import org.thoughtcrime.securesms.util.Projection;
import org.thoughtcrime.securesms.util.Util;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class ConversationItemBodyBubble extends LinearLayout {

  @Nullable private List<Outliner>        outliners = Collections.emptyList();
  @Nullable private OnSizeChangedListener sizeChangedListener;

  private ClipProjectionDrawable clipProjectionDrawable;
  private Projection             quoteViewProjection;
  private Projection             videoPlayerProjection;

  private final BodyBubbleLayoutTransition bodyBubbleLayoutTransition = new BodyBubbleLayoutTransition();

  public ConversationItemBodyBubble(Context context) {
    super(context);
    init();
  }

  public ConversationItemBodyBubble(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  public ConversationItemBodyBubble(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init();
  }

  private void init() {
    setLayoutTransition(bodyBubbleLayoutTransition);
  }

  public void setParentScrolling(boolean isParentScrolling) {
    if (isParentScrolling) {
      setLayoutTransition(null);
    } else {
      setLayoutTransition(bodyBubbleLayoutTransition);
    }
  }

  public void setOutliners(@NonNull List<Outliner> outliners) {
    this.outliners = outliners;
  }

  public void setOnSizeChangedListener(@Nullable OnSizeChangedListener listener) {
    this.sizeChangedListener = listener;
  }

  @Override
  public void setBackground(Drawable background) {
    clipProjectionDrawable = new ClipProjectionDrawable(background);

    clipProjectionDrawable.setProjections(getProjections());
    super.setBackground(clipProjectionDrawable);
  }

  public void setQuoteViewProjection(@Nullable Projection quoteViewProjection) {
    if (this.quoteViewProjection != null) {
      this.quoteViewProjection.release();
    }

    this.quoteViewProjection = quoteViewProjection;
    clipProjectionDrawable.setProjections(getProjections());
  }

  public void setVideoPlayerProjection(@Nullable Projection videoPlayerProjection) {
    if (this.videoPlayerProjection != null) {
      this.videoPlayerProjection.release();
    }

    this.videoPlayerProjection = videoPlayerProjection;
    clipProjectionDrawable.setProjections(getProjections());
  }

  public @Nullable Projection getVideoPlayerProjection() {
    return videoPlayerProjection;
  }

  public @NonNull Set<Projection> getProjections() {
    return Stream.of(quoteViewProjection, videoPlayerProjection)
                 .filterNot(Objects::isNull)
                 .collect(Collectors.toSet());
  }

  @Override
  public void onDrawForeground(Canvas canvas) {
    super.onDrawForeground(canvas);

    if (Util.isEmpty(outliners)) return;

    for (Outliner outliner : outliners) {
      outliner.draw(canvas, 0, getMeasuredWidth(), getMeasuredHeight(), 0);
    }
  }

  @Override
  protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
    if (sizeChangedListener != null) {
      post(() -> {
        if (sizeChangedListener != null) {
          sizeChangedListener.onSizeChanged(width, height);
        }
      });
    }
  }

  public interface OnSizeChangedListener {
    void onSizeChanged(int width, int height);
  }
}

