package org.thoughtcrime.securesms.components.webrtc;

import android.annotation.SuppressLint;
import android.graphics.Point;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.view.GestureDetectorCompat;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.views.TouchInterceptingFrameLayout;

import java.util.Arrays;

public class PictureInPictureGestureHelper extends GestureDetector.SimpleOnGestureListener {

  private static final float        DECELERATION_RATE   = 0.99f;
  private static final Interpolator FLING_INTERPOLATOR  = new ViscousFluidInterpolator();
  private static final Interpolator ADJUST_INTERPOLATOR = new AccelerateDecelerateInterpolator();

  private final ViewGroup parent;
  private final View      child;
  private final int       framePadding;

  private int             pipWidth;
  private int             pipHeight;
  private int             activePointerId        = MotionEvent.INVALID_POINTER_ID;
  private float           lastTouchX;
  private float           lastTouchY;
  private int             extraPaddingTop;
  private int             extraPaddingBottom;
  private double          projectionX;
  private double          projectionY;
  private VelocityTracker velocityTracker;
  private int             maximumFlingVelocity;
  private boolean         isLockedToBottomEnd;
  private Interpolator    interpolator;
  private Corner          currentCornerPosition     = Corner.BOTTOM_RIGHT;
  private int             previousTopBoundary       = -1;
  private int             expandedVerticalBoundary  = -1;
  private int             collapsedVerticalBoundary = -1;
  private BoundaryState   boundaryState             = BoundaryState.EXPANDED;
  private boolean         isCollapsedStateAllowed   = false;

  @SuppressLint("ClickableViewAccessibility")
  public static PictureInPictureGestureHelper applyTo(@NonNull View child) {
    TouchInterceptingFrameLayout  parent          = (TouchInterceptingFrameLayout) child.getParent();
    PictureInPictureGestureHelper helper          = new PictureInPictureGestureHelper(parent, child);
    GestureDetectorCompat         gestureDetector = new GestureDetectorCompat(child.getContext(), helper);

    parent.setOnInterceptTouchEventListener((event) -> {
      final int action       = event.getAction();
      final int pointerIndex = (action & MotionEvent.ACTION_POINTER_INDEX_MASK) >> MotionEvent.ACTION_POINTER_INDEX_SHIFT;

      if (pointerIndex > 0) {
        return false;
      }

      if (helper.velocityTracker == null) {
        helper.velocityTracker = VelocityTracker.obtain();
      }

      helper.velocityTracker.addMovement(event);

      return false;
    });

    parent.setOnTouchListener((v, event) -> {
      if (helper.velocityTracker != null) {
        helper.velocityTracker.recycle();
        helper.velocityTracker = null;
      }

      return false;
    });

    child.setOnTouchListener((v, event) -> {
      boolean handled = gestureDetector.onTouchEvent(event);

      if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
        if (!handled) {
          handled = helper.onGestureFinished(event);
        }

        if (helper.velocityTracker != null) {
          helper.velocityTracker.recycle();
          helper.velocityTracker = null;
        }
      }

      return handled;
    });

    return helper;
  }

  private PictureInPictureGestureHelper(@NonNull ViewGroup parent, @NonNull View child) {
    this.parent               = parent;
    this.child                = child;
    this.framePadding         = child.getResources().getDimensionPixelSize(R.dimen.picture_in_picture_gesture_helper_frame_padding);
    this.pipWidth             = child.getResources().getDimensionPixelSize(R.dimen.picture_in_picture_gesture_helper_pip_width);
    this.pipHeight            = child.getResources().getDimensionPixelSize(R.dimen.picture_in_picture_gesture_helper_pip_height);
    this.maximumFlingVelocity = ViewConfiguration.get(child.getContext()).getScaledMaximumFlingVelocity();
    this.interpolator         = ADJUST_INTERPOLATOR;
  }

  public void setTopVerticalBoundary(int topBoundary) {
    if (topBoundary == previousTopBoundary) {
      return;
    }
    previousTopBoundary = topBoundary;

    extraPaddingTop = topBoundary - parent.getTop();

    ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams) child.getLayoutParams();
    layoutParams.setMargins(layoutParams.leftMargin, extraPaddingTop + framePadding, layoutParams.rightMargin, layoutParams.bottomMargin);
    child.setLayoutParams(layoutParams);
  }

  public void setCollapsedVerticalBoundary(int bottomBoundary) {
    final int oldBoundary = collapsedVerticalBoundary;
    collapsedVerticalBoundary = bottomBoundary;

    if (oldBoundary != bottomBoundary && boundaryState == BoundaryState.COLLAPSED) {
      applyBottomVerticalBoundary(bottomBoundary);
    }
  }

  public void setExpandedVerticalBoundary(int bottomBoundary) {
    final int oldBoundary = expandedVerticalBoundary;
    expandedVerticalBoundary = bottomBoundary;

    if (oldBoundary != bottomBoundary && boundaryState == BoundaryState.EXPANDED) {
      applyBottomVerticalBoundary(bottomBoundary);
    }
  }

  public void setBoundaryState(@NonNull BoundaryState boundaryState) {
    if (!isCollapsedStateAllowed && boundaryState == BoundaryState.COLLAPSED) {
      return;
    }

    final BoundaryState old = this.boundaryState;
    this.boundaryState = boundaryState;
    if (old != boundaryState) {
      applyBottomVerticalBoundary(boundaryState == BoundaryState.EXPANDED ? expandedVerticalBoundary : collapsedVerticalBoundary);
    }
  }

  public void allowCollapsedState() {
    if (isCollapsedStateAllowed) {
      return;
    }

    isCollapsedStateAllowed = true;
    setBoundaryState(BoundaryState.COLLAPSED);
  }

  private void applyBottomVerticalBoundary(int bottomBoundary) {
    extraPaddingBottom = parent.getMeasuredHeight() + parent.getTop() - bottomBoundary;
    ViewUtil.setBottomMargin(child, extraPaddingBottom + framePadding);
  }

  private boolean onGestureFinished(MotionEvent e) {
    final int pointerIndex = e.findPointerIndex(activePointerId);

    if (e.getActionIndex() == pointerIndex) {
      onFling(e, e, 0, 0);
      return true;
    }

    return false;
  }

  public void lockToBottomEnd() {
    isLockedToBottomEnd = true;
    fling();
  }

  public void enableCorners() {
    isLockedToBottomEnd = false;
  }

  @Override
  public boolean onDown(MotionEvent e) {
    activePointerId = e.getPointerId(0);
    lastTouchX      = e.getX(0) + child.getX();
    lastTouchY      = e.getY(0) + child.getY();
    pipWidth        = child.getMeasuredWidth();
    pipHeight       = child.getMeasuredHeight();
    interpolator    = FLING_INTERPOLATOR;

    return true;
  }

  @Override
  public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
    if (isLockedToBottomEnd) {
      return false;
    }

    int pointerIndex = e2.findPointerIndex(activePointerId);

    if (pointerIndex == -1) {
      fling();
      return false;
    }

    float x  = e2.getX(pointerIndex) + child.getX();
    float y  = e2.getY(pointerIndex) + child.getY();
    float dx = x - lastTouchX;
    float dy = y - lastTouchY;

    child.setTranslationX(child.getTranslationX() + dx);
    child.setTranslationY(child.getTranslationY() + dy);

    lastTouchX = x;
    lastTouchY = y;

    return true;
  }

  @Override
  public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
    if (isLockedToBottomEnd) {
      return false;
    }

    if (velocityTracker != null) {
      velocityTracker.computeCurrentVelocity(1000, maximumFlingVelocity);

      projectionX = child.getX() + project(velocityTracker.getXVelocity());
      projectionY = child.getY() + project(velocityTracker.getYVelocity());
    } else {
      projectionX = child.getX();
      projectionY = child.getY();
    }

    fling();

    return true;
  }

  @Override
  public boolean onSingleTapUp(MotionEvent e) {
    child.performClick();
    return true;
  }

  private void fling() {
    Point  projection            = new Point((int) projectionX, (int) projectionY);
    Corner nearestCornerPosition = findNearestCornerPosition(projection);

    FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) child.getLayoutParams();
    layoutParams.gravity = nearestCornerPosition.gravity;

    if (currentCornerPosition != null && currentCornerPosition != nearestCornerPosition) {
      adjustTranslationFrameOfReference(child, currentCornerPosition, nearestCornerPosition);
    }
    currentCornerPosition = nearestCornerPosition;

    child.setLayoutParams(layoutParams);

    child.animate()
         .translationX(0)
         .translationY(0)
         .setDuration(250)
         .setInterpolator(interpolator)
         .start();
  }

  private Corner findNearestCornerPosition(Point projection) {
    if (isLockedToBottomEnd) {
      return ViewUtil.isLtr(parent) ? Corner.BOTTOM_RIGHT
                                    : Corner.BOTTOM_LEFT;
    }

    CornerPoint maxPoint    = null;
    double      maxDistance = Double.MAX_VALUE;

    for (CornerPoint cornerPoint : Arrays.asList(calculateTopLeftCoordinates(),
                                                 calculateTopRightCoordinates(parent),
                                                 calculateBottomLeftCoordinates(parent),
                                                 calculateBottomRightCoordinates(parent))) {
      double distance = distance(cornerPoint.point, projection);

      if (distance < maxDistance) {
        maxDistance = distance;
        maxPoint    = cornerPoint;
      }
    }

    //noinspection DataFlowIssue
    return maxPoint.corner;
  }

  private CornerPoint calculateTopLeftCoordinates() {
    return new CornerPoint(new Point(framePadding, framePadding + extraPaddingTop),
                           Corner.TOP_LEFT);
  }

  private CornerPoint calculateTopRightCoordinates(@NonNull ViewGroup parent) {
    return new CornerPoint(new Point(parent.getMeasuredWidth() - pipWidth - framePadding, framePadding + extraPaddingTop),
                           Corner.TOP_RIGHT);
  }

  private CornerPoint calculateBottomLeftCoordinates(@NonNull ViewGroup parent) {
    return new CornerPoint(new Point(framePadding, parent.getMeasuredHeight() - pipHeight - framePadding - extraPaddingBottom),
                           Corner.BOTTOM_LEFT);
  }

  private CornerPoint calculateBottomRightCoordinates(@NonNull ViewGroup parent) {
    return new CornerPoint(new Point(parent.getMeasuredWidth() - pipWidth - framePadding, parent.getMeasuredHeight() - pipHeight - framePadding - extraPaddingBottom),
                           Corner.BOTTOM_RIGHT);
  }

  private static float project(float initialVelocity) {
    return (initialVelocity / 1000f) * DECELERATION_RATE / (1f - DECELERATION_RATE);
  }

  private static double distance(Point a, Point b) {
    return Math.sqrt(Math.pow(a.x - b.x, 2) + Math.pow(a.y - b.y, 2));
  }


  /**
   * User drag is implemented by translating the view from the current gravity anchor (corner). When the user drags
   * to a new corner, we need to adjust the translations for the new corner so the animation of translation X/Y to 0
   * works correctly.
   *
   * For example, if in bottom right and need to move to top right, we need to calculate a new translation Y since instead
   * of being translated up from bottom it's translated down from the top.
   */
  private void adjustTranslationFrameOfReference(@NonNull View child, @NonNull Corner previous, @NonNull Corner next) {
    TouchInterceptingFrameLayout parent            = (TouchInterceptingFrameLayout) child.getParent();
    FrameLayout.LayoutParams     childLayoutParams = (FrameLayout.LayoutParams) child.getLayoutParams();
    int                          parentWidth       = parent.getWidth();
    int                          parentHeight      = parent.getHeight();

    if (previous.topHalf != next.topHalf) {
      int childHeight = childLayoutParams.height + childLayoutParams.topMargin + childLayoutParams.bottomMargin;

      float adjustedTranslationY;
      if (previous.topHalf) {
        adjustedTranslationY = -(parentHeight - child.getTranslationY() - childHeight);
      } else {
        adjustedTranslationY = parentHeight + child.getTranslationY() - childHeight;
      }
      child.setTranslationY(adjustedTranslationY);
    }

    if (previous.leftSide != next.leftSide) {
      int childWidth = childLayoutParams.width + childLayoutParams.leftMargin + childLayoutParams.rightMargin;

      float adjustedTranslationX;
      if (previous.leftSide) {
        adjustedTranslationX = -(parentWidth - child.getTranslationX() - childWidth);
      } else {
        adjustedTranslationX = parentWidth + child.getTranslationX() - childWidth;
      }
      child.setTranslationX(adjustedTranslationX);
    }
  }

  private static class CornerPoint {
    final Point  point;
    final Corner corner;

    public CornerPoint(@NonNull Point point, @NonNull Corner corner) {
      this.point  = point;
      this.corner = corner;
    }
  }

  @SuppressLint("RtlHardcoded")
  private enum Corner {
    TOP_LEFT(Gravity.TOP | Gravity.LEFT, true, true),
    TOP_RIGHT(Gravity.TOP | Gravity.RIGHT, false, true),
    BOTTOM_LEFT(Gravity.BOTTOM | Gravity.LEFT, true, false),
    BOTTOM_RIGHT(Gravity.BOTTOM | Gravity.RIGHT, false, false);

    final int     gravity;
    final boolean leftSide;
    final boolean topHalf;

    Corner(int gravity, boolean leftSide, boolean topHalf) {
      this.gravity  = gravity;
      this.leftSide = leftSide;
      this.topHalf  = topHalf;
    }
  }

  /**
   * Borrowed from ScrollView
   */
  private static class ViscousFluidInterpolator implements Interpolator {
    /**
     * Controls the viscous fluid effect (how much of it).
     */
    private static final float VISCOUS_FLUID_SCALE = 8.0f;

    private static final float VISCOUS_FLUID_NORMALIZE;
    private static final float VISCOUS_FLUID_OFFSET;

    static {

      // must be set to 1.0 (used in viscousFluid())
      VISCOUS_FLUID_NORMALIZE = 1.0f / viscousFluid(1.0f);
      // account for very small floating-point error
      VISCOUS_FLUID_OFFSET = 1.0f - VISCOUS_FLUID_NORMALIZE * viscousFluid(1.0f);
    }

    private static float viscousFluid(float x) {
      x *= VISCOUS_FLUID_SCALE;
      if (x < 1.0f) {
        x -= (1.0f - (float) Math.exp(-x));
      } else {
        float start = 0.36787944117f;   // 1/e == exp(-1)
        x = 1.0f - (float) Math.exp(1.0f - x);
        x = start + x * (1.0f - start);
      }
      return x;
    }

    @Override
    public float getInterpolation(float input) {
      final float interpolated = VISCOUS_FLUID_NORMALIZE * viscousFluid(input);
      if (interpolated > 0) {
        return interpolated + VISCOUS_FLUID_OFFSET;
      }
      return interpolated;
    }
  }

  public enum BoundaryState {
    EXPANDED,
    COLLAPSED
  }
}
