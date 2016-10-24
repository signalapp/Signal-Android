// From https://gist.github.com/mipreamble/b6d4b3d65b0b4775a22e#file-recyclerviewpositionhelper-java

package org.thoughtcrime.securesms.giph.util;


import android.support.v7.widget.OrientationHelper;
import android.support.v7.widget.RecyclerView;
import android.view.View;

public class RecyclerViewPositionHelper {

  final RecyclerView recyclerView;
  final RecyclerView.LayoutManager layoutManager;

  RecyclerViewPositionHelper(RecyclerView recyclerView) {
    this.recyclerView = recyclerView;
    this.layoutManager = recyclerView.getLayoutManager();
  }

  public static RecyclerViewPositionHelper createHelper(RecyclerView recyclerView) {
    if (recyclerView == null) {
      throw new NullPointerException("Recycler View is null");
    }
    return new RecyclerViewPositionHelper(recyclerView);
  }

  /**
   * Returns the adapter item count.
   *
   * @return The total number on items in a layout manager
   */
  public int getItemCount() {
    return layoutManager == null ? 0 : layoutManager.getItemCount();
  }

  /**
   * Returns the adapter position of the first visible view. This position does not include
   * adapter changes that were dispatched after the last layout pass.
   *
   * @return The adapter position of the first visible item or {@link RecyclerView#NO_POSITION} if
   * there aren't any visible items.
   */
  public int findFirstVisibleItemPosition() {
    final View child = findOneVisibleChild(0, layoutManager.getChildCount(), false, true);
    return child == null ? RecyclerView.NO_POSITION : recyclerView.getChildAdapterPosition(child);
  }

  /**
   * Returns the adapter position of the first fully visible view. This position does not include
   * adapter changes that were dispatched after the last layout pass.
   *
   * @return The adapter position of the first fully visible item or
   * {@link RecyclerView#NO_POSITION} if there aren't any visible items.
   */
  public int findFirstCompletelyVisibleItemPosition() {
    final View child = findOneVisibleChild(0, layoutManager.getChildCount(), true, false);
    return child == null ? RecyclerView.NO_POSITION : recyclerView.getChildAdapterPosition(child);
  }

  /**
   * Returns the adapter position of the last visible view. This position does not include
   * adapter changes that were dispatched after the last layout pass.
   *
   * @return The adapter position of the last visible view or {@link RecyclerView#NO_POSITION} if
   * there aren't any visible items
   */
  public int findLastVisibleItemPosition() {
    final View child = findOneVisibleChild(layoutManager.getChildCount() - 1, -1, false, true);
    return child == null ? RecyclerView.NO_POSITION : recyclerView.getChildAdapterPosition(child);
  }

  /**
   * Returns the adapter position of the last fully visible view. This position does not include
   * adapter changes that were dispatched after the last layout pass.
   *
   * @return The adapter position of the last fully visible view or
   * {@link RecyclerView#NO_POSITION} if there aren't any visible items.
   */
  public int findLastCompletelyVisibleItemPosition() {
    final View child = findOneVisibleChild(layoutManager.getChildCount() - 1, -1, true, false);
    return child == null ? RecyclerView.NO_POSITION : recyclerView.getChildAdapterPosition(child);
  }

  View findOneVisibleChild(int fromIndex, int toIndex, boolean completelyVisible,
                           boolean acceptPartiallyVisible) {
    OrientationHelper helper;
    if (layoutManager.canScrollVertically()) {
      helper = OrientationHelper.createVerticalHelper(layoutManager);
    } else {
      helper = OrientationHelper.createHorizontalHelper(layoutManager);
    }

    final int start = helper.getStartAfterPadding();
    final int end = helper.getEndAfterPadding();
    final int next = toIndex > fromIndex ? 1 : -1;
    View partiallyVisible = null;
    for (int i = fromIndex; i != toIndex; i += next) {
      final View child = layoutManager.getChildAt(i);
      final int childStart = helper.getDecoratedStart(child);
      final int childEnd = helper.getDecoratedEnd(child);
      if (childStart < end && childEnd > start) {
        if (completelyVisible) {
          if (childStart >= start && childEnd <= end) {
            return child;
          } else if (acceptPartiallyVisible && partiallyVisible == null) {
            partiallyVisible = child;
          }
        } else {
          return child;
        }
      }
    }
    return partiallyVisible;
  }
}