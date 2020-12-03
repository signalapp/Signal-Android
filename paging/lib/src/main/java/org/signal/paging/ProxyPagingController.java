package org.signal.paging;

import android.util.Log;

import androidx.annotation.Nullable;

/**
 * A controller that forwards calls to a secondary, proxied controller. This is useful when you want
 * to keep a single, static controller, even when the true controller may be changing due to data
 * source changes.
 */
public class ProxyPagingController implements PagingController {

  private PagingController proxied;

  @Override
  public synchronized void onDataNeededAroundIndex(int aroundIndex) {
    if (proxied != null) {
      proxied.onDataNeededAroundIndex(aroundIndex);
    }
  }

  @Override
  public synchronized void onDataInvalidated() {
    if (proxied != null) {
      proxied.onDataInvalidated();
    }
  }

  /**
   * Updates the underlying controller to the one specified.
   */
  public synchronized void set(@Nullable PagingController bound) {
    this.proxied = bound;
  }
}
