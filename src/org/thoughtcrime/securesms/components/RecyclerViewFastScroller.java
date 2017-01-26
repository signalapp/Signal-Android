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
import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build.VERSION;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.ViewUtil;

public class RecyclerViewFastScroller extends LinearLayout {
  private static final int BUBBLE_ANIMATION_DURATION = 100;
  private static final int TRACK_SNAP_RANGE          = 5;

  private @NonNull  TextView     bubble;
  private @NonNull  View         handle;
  private @Nullable RecyclerView recyclerView;

  private int            height;
  private ObjectAnimator currentAnimator;

  private final RecyclerView.OnScrollListener onScrollListener = new RecyclerView.OnScrollListener() {
    @Override
    public void onScrolled(final RecyclerView recyclerView, final int dx, final int dy) {
      if (handle.isSelected()) return;
      computeBubbleAndHandlePosition();
    }
  };

  public interface FastScrollAdapter {
    CharSequence getBubbleText(int pos);
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
    bubble = ViewUtil.findById(this, R.id.fastscroller_bubble);
    handle = ViewUtil.findById(this, R.id.fastscroller_handle);
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    height = h;
    computeBubbleAndHandlePosition();
  }

  @Override
  @TargetApi(11)
  public boolean onTouchEvent(@NonNull MotionEvent event) {
    final int action = event.getAction();
    switch (action) {
    case MotionEvent.ACTION_DOWN:
      if (event.getX() < ViewUtil.getX(handle) - handle.getPaddingLeft() ||
          event.getY() < ViewUtil.getY(handle) - handle.getPaddingTop() ||
          event.getY() > ViewUtil.getY(handle) + handle.getHeight() + handle.getPaddingBottom())
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

  public void setRecyclerView(final @NonNull RecyclerView recyclerView) {
    if (this.recyclerView != null) {
      this.recyclerView.removeOnScrollListener(onScrollListener);
    }
    this.recyclerView = recyclerView;
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
      if (ViewUtil.getY(handle) == 0) {
        proportion = 0f;
      } else if (ViewUtil.getY(handle) + handle.getHeight() >= height - TRACK_SNAP_RANGE) {
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
    ViewUtil.setY(handle, handleY);
    ViewUtil.setY(bubble, Util.clamp(handleY - bubbleHeight - bubble.getPaddingBottom() + handleHeight,
                                     0,
                                     height - bubbleHeight));
  }

  private void computeBubbleAndHandlePosition() {
    if (recyclerView != null) {
      final int offset      = recyclerView.computeVerticalScrollOffset();
      final int range       = recyclerView.computeVerticalScrollRange();
      final int extent      = recyclerView.computeVerticalScrollExtent();
      final int offsetRange = Math.max(range - extent, 1);
      setBubbleAndHandlePosition((float) Util.clamp(offset, 0, offsetRange) / offsetRange);
    }
  }

  @TargetApi(11)
  private void showBubble() {
    bubble.setVisibility(VISIBLE);
    if (VERSION.SDK_INT >= 11) {
      if (currentAnimator != null) currentAnimator.cancel();
      currentAnimator = ObjectAnimator.ofFloat(bubble, "alpha", 0f, 1f).setDuration(BUBBLE_ANIMATION_DURATION);
      currentAnimator.start();
    }
  }

  @TargetApi(11)
  private void hideBubble() {
    if (VERSION.SDK_INT >= 11) {
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
    } else {
      bubble.setVisibility(INVISIBLE);
    }
  }
}
