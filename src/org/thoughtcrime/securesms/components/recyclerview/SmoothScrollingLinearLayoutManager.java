package org.thoughtcrime.securesms.components.recyclerview;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.LinearSmoothScroller;
import android.util.DisplayMetrics;

public class SmoothScrollingLinearLayoutManager extends LinearLayoutManager {

  public SmoothScrollingLinearLayoutManager(Context context, boolean reverseLayout) {
    super(context, LinearLayoutManager.VERTICAL, reverseLayout);
  }

  public void smoothScrollToPosition(@NonNull Context context, int position, float millisecondsPerInch) {
    final LinearSmoothScroller scroller = new LinearSmoothScroller(context) {
      @Override
      protected int getVerticalSnapPreference() {
        return LinearSmoothScroller.SNAP_TO_END;
      }

      @Override
      protected float calculateSpeedPerPixel(DisplayMetrics displayMetrics) {
        return millisecondsPerInch / displayMetrics.densityDpi;
      }
    };

    scroller.setTargetPosition(position);
    startSmoothScroll(scroller);
  }
}
