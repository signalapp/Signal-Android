package org.thoughtcrime.securesms.components.recyclerview;

import android.content.Context;
import android.util.DisplayMetrics;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;

public class SmoothScrollingLinearLayoutManager extends LinearLayoutManager {

  public SmoothScrollingLinearLayoutManager(Context context, boolean reverseLayout) {
    super(context, RecyclerView.VERTICAL, reverseLayout);
  }

  @Override
  public boolean supportsPredictiveItemAnimations() {
    return false;
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
