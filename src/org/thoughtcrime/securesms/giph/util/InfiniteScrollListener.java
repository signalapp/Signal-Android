// From https://gist.github.com/mipreamble/b6d4b3d65b0b4775a22e#file-recyclerviewpositionhelper-java

package org.thoughtcrime.securesms.giph.util;

import android.support.v7.widget.RecyclerView;

public abstract class InfiniteScrollListener extends RecyclerView.OnScrollListener {

  public static String TAG = InfiniteScrollListener.class.getSimpleName();

  private int     previousTotal    = 0;    // The total number of items in the dataset after the last load
  private boolean loading          = true; // True if we are still waiting for the last set of data to load.
  private int     visibleThreshold = 5;    // The minimum amount of items to have below your current scroll position before loading more.

  int firstVisibleItem, visibleItemCount, totalItemCount;

  private int currentPage = 1;

  @Override
  public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
    super.onScrolled(recyclerView, dx, dy);

    RecyclerViewPositionHelper recyclerViewPositionHelper = RecyclerViewPositionHelper.createHelper(recyclerView);

    visibleItemCount = recyclerView.getChildCount();
    totalItemCount   = recyclerViewPositionHelper.getItemCount();
    firstVisibleItem = recyclerViewPositionHelper.findFirstVisibleItemPosition();

    if (loading) {
      if (totalItemCount > previousTotal) {
        loading = false;
        previousTotal = totalItemCount;
      }
    }
    if (!loading && (totalItemCount - visibleItemCount)
        <= (firstVisibleItem + visibleThreshold)) {
      // End has been reached
      // Do something
      currentPage++;

      onLoadMore(currentPage);

      loading = true;
    }
  }

  public abstract void onLoadMore(int currentPage);
}