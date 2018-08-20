package org.thoughtcrime.securesms.components.viewpager;


import android.content.Context;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import org.thoughtcrime.securesms.logging.Log;
import android.view.MotionEvent;

/**
 * Hacky fix for http://code.google.com/p/android/issues/detail?id=18990
 * <p/>
 * ScaleGestureDetector seems to mess up the touch events, which means that
 * ViewGroups which make use of onInterceptTouchEvent throw a lot of
 * IllegalArgumentException: pointerIndex out of range.
 * <p/>
 * There's not much I can do in my code for now, but we can mask the result by
 * just catching the problem and ignoring it.
 *
 * @author Chris Banes
 */
public class HackyViewPager extends ViewPager {

  private static final String TAG = HackyViewPager.class.getSimpleName();

  public HackyViewPager(Context context) {
    super(context);
  }

  public HackyViewPager(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  @Override
  public boolean onInterceptTouchEvent(MotionEvent ev) {
    try {
      return super.onInterceptTouchEvent(ev);
    } catch (IllegalArgumentException e) {
      Log.w(TAG, e);
      return false;
    }
  }
}