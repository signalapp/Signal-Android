package org.thoughtcrime.securesms.components.webrtc;

import android.animation.Animator;
import android.annotation.SuppressLint;
import android.graphics.Point;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Interpolator;

import androidx.annotation.NonNull;
import androidx.core.view.GestureDetectorCompat;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.animation.AnimationCompleteListener;
import org.thoughtcrime.securesms.util.views.TouchInterceptingFrameLayout;

import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;

public class PictureInPictureGestureHelper extends GestureDetector.SimpleOnGestureListener {

  private static final float        DECELERATION_RATE   = 0.99f;
  private static final Interpolator FLING_INTERPOLATOR  = new ViscousFluidInterpolator();
  private static final Interpolator ADJUST_INTERPOLATOR = new AccelerateDecelerateInterpolator();

  private final ViewGroup       parent;
  private final View            child;
  private final int             framePadding;
  private final Queue<Runnable> runAfterFling;

  private int             pipWidth;
  private int             pipHeight;
  private int             activePointerId = MotionEvent.INVALID_POINTER_ID;
  private float           lastTouchX;
  private float           lastTouchY;
  private boolean         isDragging;
  private boolean         isAnimating;
  private int             extraPaddingTop;
  private int             extraPaddingBottom;
  private double          projectionX;
  private double          projectionY;
  private VelocityTracker velocityTracker;
  private int             maximumFlingVelocity;
  private boolean         isLockedToBottomEnd;
  private Interpolator    interpolator;

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
    this.runAfterFling        = new LinkedList<>();
    this.interpolator         = ADJUST_INTERPOLATOR;
  }

  public void clearVerticalBoundaries() {
    setVerticalBoundaries(parent.getTop(), parent.getMeasuredHeight() + parent.getTop());
  }

  public void setVerticalBoundaries(int topBoundary, int bottomBoundary) {
    extraPaddingTop    = topBoundary - parent.getTop();
    extraPaddingBottom = parent.getMeasuredHeight() + parent.getTop() - bottomBoundary;

    adjustPip();
  }

  private boolean onGestureFinished(MotionEvent e) {
    final int pointerIndex = e.findPointerIndex(activePointerId);

    if (e.getActionIndex() == pointerIndex) {
      onFling(e, e, 0, 0);
      return true;
    }

    return false;
  }

  public void adjustPip() {
    pipWidth  = child.getMeasuredWidth();
    pipHeight = child.getMeasuredHeight();

    if (isAnimating) {
      interpolator = ADJUST_INTERPOLATOR;

      fling();
    } else if (!isDragging) {
      interpolator = ADJUST_INTERPOLATOR;

      onFling(null, null, 0, 0);
    }
  }

  public void lockToBottomEnd() {
    isLockedToBottomEnd = true;
  }

  public void enableCorners() {
    isLockedToBottomEnd = false;
  }

  public void performAfterFling(@NonNull Runnable runnable) {
    if (isAnimating) {
      runAfterFling.add(runnable);
    } else {
      runnable.run();
    }
  }

  @Override
  public boolean onDown(MotionEvent e) {
    activePointerId = e.getPointerId(0);
    lastTouchX      = e.getX(0) + child.getX();
    lastTouchY      = e.getY(0) + child.getY();
    isDragging      = true;
    pipWidth        = child.getMeasuredWidth();
    pipHeight       = child.getMeasuredHeight();
    interpolator    = FLING_INTERPOLATOR;

    return true;
  }

  @Override
  public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
    int pointerIndex = e2.findPointerIndex(activePointerId);

    if (pointerIndex == -1) {
      fling();
      return false;
    }

    float x            = e2.getX(pointerIndex) + child.getX();
    float y            = e2.getY(pointerIndex) + child.getY();
    float dx           = x - lastTouchX;
    float dy           = y - lastTouchY;

    child.setTranslationX(child.getTranslationX() + dx);
    child.setTranslationY(child.getTranslationY() + dy);

    lastTouchX = x;
    lastTouchY = y;

    return true;
  }

  @Override
  public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
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
    isDragging = false;

    child.performClick();
    return true;
  }

  private void fling() {
    Point  projection            = new Point((int) projectionX, (int) projectionY);
    Point  nearestCornerPosition = findNearestCornerPosition(projection);

    isAnimating = true;
    isDragging  = false;

    child.animate()
         .translationX(getTranslationXForPoint(nearestCornerPosition))
         .translationY(getTranslationYForPoint(nearestCornerPosition))
         .setDuration(250)
         .setInterpolator(interpolator)
         .setListener(new AnimationCompleteListener() {
           @Override
           public void onAnimationEnd(Animator animation) {
             isAnimating = false;

             Iterator<Runnable> afterFlingRunnables = runAfterFling.iterator();
             while (afterFlingRunnables.hasNext()) {
               Runnable runnable = afterFlingRunnables.next();

               runnable.run();
               afterFlingRunnables.remove();
             }
           }
         })
         .start();
  }

  private Point findNearestCornerPosition(Point projection) {
    if (isLockedToBottomEnd) {
      return parent.getLayoutDirection() == View.LAYOUT_DIRECTION_LTR ? calculateBottomRightCoordinates(parent)
                                                                      : calculateBottomLeftCoordinates(parent);
    }

    Point  maxPoint     = null;
    double maxDistance  = Double.MAX_VALUE;

    for (Point point : Arrays.asList(calculateTopLeftCoordinates(),
                                     calculateTopRightCoordinates(parent),
                                     calculateBottomLeftCoordinates(parent),
                                     calculateBottomRightCoordinates(parent)))
    {
      double distance = distance(point, projection);

      if (distance < maxDistance) {
        maxDistance = distance;
        maxPoint    = point;
      }
    }

    return maxPoint;
  }

  private float getTranslationXForPoint(Point destination) {
    return destination.x - child.getLeft();
  }

  private float getTranslationYForPoint(Point destination) {
    return destination.y - child.getTop();
  }

  private Point calculateTopLeftCoordinates() {
    return new Point(framePadding,
                     framePadding + extraPaddingTop);
  }

  private Point calculateTopRightCoordinates(@NonNull ViewGroup parent) {
    return new Point(parent.getMeasuredWidth() - pipWidth - framePadding,
                     framePadding + extraPaddingTop);
  }

  private Point calculateBottomLeftCoordinates(@NonNull ViewGroup parent) {
    return new Point(framePadding,
                     parent.getMeasuredHeight() - pipHeight - framePadding - extraPaddingBottom);
  }

  private Point calculateBottomRightCoordinates(@NonNull ViewGroup parent) {
    return new Point(parent.getMeasuredWidth() - pipWidth - framePadding,
                     parent.getMeasuredHeight() - pipHeight - framePadding - extraPaddingBottom);
  }

  private static float project(float initialVelocity) {
    return (initialVelocity / 1000f) * DECELERATION_RATE / (1f - DECELERATION_RATE);
  }

  private static double distance(Point a, Point b) {
    return Math.sqrt(Math.pow(a.x - b.x, 2) + Math.pow(a.y - b.y, 2));
  }

  /** Borrowed from ScrollView */
  private static class ViscousFluidInterpolator implements Interpolator {
    /** Controls the viscous fluid effect (how much of it). */
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
        x -= (1.0f - (float)Math.exp(-x));
      } else {
        float start = 0.36787944117f;   // 1/e == exp(-1)
        x = 1.0f - (float)Math.exp(1.0f - x);
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
}
