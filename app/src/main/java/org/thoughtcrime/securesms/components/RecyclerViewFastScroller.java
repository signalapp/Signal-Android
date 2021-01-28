/**
 * Modified version of
 * https://github.com/AndroidDeveloperLB/LollipopContactsRecyclerViewFastScroller
 *
 * Their license:
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thoughtcrime.securesms.components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.Util;

public final class RecyclerViewFastScroller extends LinearLayout {
  private static final int BUBBLE_ANIMATION_DURATION = 100;
  private static final int TRACK_SNAP_RANGE          = 5;

  @NonNull  private final TextView     bubble;
  @NonNull  private final View         handle;
  @Nullable private       RecyclerView recyclerView;

  private int            height;
  private ObjectAnimator currentAnimator;

  private final RecyclerView.OnScrollListener onScrollListener = new RecyclerView.OnScrollListener() {
    @Override
    public void onScrolled(@NonNull final RecyclerView recyclerView, final int dx, final int dy) {
      if (handle.isSelected()) return;
      final int   offset      = recyclerView.computeVerticalScrollOffset();
      final int   range       = recyclerView.computeVerticalScrollRange();
      final int   extent      = recyclerView.computeVerticalScrollExtent();
      final int   offsetRange = Math.max(range - extent, 1);
      setBubbleAndHandlePosition((float) Util.clamp(offset, 0, offsetRange) / offsetRange);
    }
  };

  public interface FastScrollAdapter {
    CharSequence getBubbleText(int position);
  }

  public RecyclerViewFastScroller(final Context context) {
    this(context, null);
  }

  public RecyclerViewFastScroller(final Context context, final AttributeSet attrs) {
    super(context, attrs);
    setOrientation(HORIZONTAL);
    setClipChildren(false);
    setScrollContainer(true);
    inflate(context, R.layout.recycler_view_fast_scroller, this);
    bubble = findViewById(R.id.fastscroller_bubble);
    handle = findViewById(R.id.fastscroller_handle);
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    height = h;
  }

  @Override
  public boolean onTouchEvent(@NonNull MotionEvent event) {
    final int action = event.getAction();
    switch (action) {
    case MotionEvent.ACTION_DOWN:
      if (event.getX() < handle.getX() - handle.getPaddingLeft() ||
          event.getY() < handle.getY() - handle.getPaddingTop() ||
          event.getY() > handle.getY() + handle.getHeight() + handle.getPaddingBottom())
      {
        return false;
      }
      if (currentAnimator != null) {
        currentAnimator.cancel();
      }
      if (bubble.getVisibility() != VISIBLE) {
        showBubble();
      }
      handle.setSelected(true);
    case MotionEvent.ACTION_MOVE:
      final float y = event.getY();
      setBubbleAndHandlePosition(y / height);
      setRecyclerViewPosition(y);
      return true;
    case MotionEvent.ACTION_UP:
    case MotionEvent.ACTION_CANCEL:
      handle.setSelected(false);
      hideBubble();
      return true;
    }
    return super.onTouchEvent(event);
  }

  public void setRecyclerView(final @Nullable RecyclerView recyclerView) {
    if (this.recyclerView != null) {
      this.recyclerView.removeOnScrollListener(onScrollListener);
    }
    this.recyclerView = recyclerView;
    if (recyclerView != null) {
      recyclerView.addOnScrollListener(onScrollListener);
      recyclerView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
        @Override
        public boolean onPreDraw() {
          recyclerView.getViewTreeObserver().removeOnPreDrawListener(this);
          if (handle.isSelected()) return true;
          final int verticalScrollOffset = recyclerView.computeVerticalScrollOffset();
          final int verticalScrollRange = recyclerView.computeVerticalScrollRange();
          float proportion = (float)verticalScrollOffset / ((float)verticalScrollRange - height);
          setBubbleAndHandlePosition(height * proportion);
          return true;
        }
      });
    }
  }

  @Override
  protected void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    if (recyclerView != null)
      recyclerView.removeOnScrollListener(onScrollListener);
  }

  private void setRecyclerViewPosition(float y) {
    if (recyclerView != null) {
      final int itemCount = recyclerView.getAdapter().getItemCount();
      float proportion;
      if (handle.getY() == 0) {
        proportion = 0f;
      } else if (handle.getY() + handle.getHeight() >= height - TRACK_SNAP_RANGE) {
        proportion = 1f;
      } else {
        proportion = y / (float)height;
      }

      final int targetPos = Util.clamp((int)(proportion * (float)itemCount), 0, itemCount - 1);
      ((LinearLayoutManager) recyclerView.getLayoutManager()).scrollToPositionWithOffset(targetPos, 0);
      final CharSequence bubbleText = ((FastScrollAdapter) recyclerView.getAdapter()).getBubbleText(targetPos);
      bubble.setText(bubbleText);
    }
  }

  private void setBubbleAndHandlePosition(float y) {
    final int handleHeight = handle.getHeight();
    final int bubbleHeight = bubble.getHeight();
    final int handleY = Util.clamp((int)((height - handleHeight) * y), 0, height - handleHeight);
    handle.setY(handleY);
    bubble.setY(Util.clamp(handleY - bubbleHeight - bubble.getPaddingBottom() + handleHeight,
                           0,
                           height - bubbleHeight));
  }

  private void showBubble() {
    bubble.setVisibility(VISIBLE);
    if (currentAnimator != null) currentAnimator.cancel();
    currentAnimator = ObjectAnimator.ofFloat(bubble, "alpha", 0f, 1f).setDuration(BUBBLE_ANIMATION_DURATION);
    currentAnimator.start();
  }

  private void hideBubble() {
    if (currentAnimator != null) currentAnimator.cancel();
    currentAnimator = ObjectAnimator.ofFloat(bubble, "alpha", 1f, 0f).setDuration(BUBBLE_ANIMATION_DURATION);
    currentAnimator.addListener(new AnimatorListenerAdapter() {
      @Override
      public void onAnimationEnd(Animator animation) {
        super.onAnimationEnd(animation);
        bubble.setVisibility(INVISIBLE);
        currentAnimator = null;
      }

      @Override
      public void onAnimationCancel(Animator animation) {
        super.onAnimationCancel(animation);
        bubble.setVisibility(INVISIBLE);
        currentAnimator = null;
      }
    });
    currentAnimator.start();
  }
}
