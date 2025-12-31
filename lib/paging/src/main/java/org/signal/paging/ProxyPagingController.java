package org.signal.paging;

import androidx.annotation.Nullable;

/**
 * A controller that forwards calls to a secondary, proxied controller. This is useful when you want
 * to keep a single, static controller, even when the true controller may be changing due to data
 * source changes.
 */
public class ProxyPagingController<Key> implements PagingController<Key> {

  private PagingController<Key> proxied;

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

  @Override
  public void onDataItemChanged(Key key) {
    if (proxied != null) {
      proxied.onDataItemChanged(key);
    }
  }

  @Override
  public void onDataItemInserted(Key key, int position) {
    if (proxied != null) {
      proxied.onDataItemInserted(key, position);
    }
  }

  /**
   * Updates the underlying controller to the one specified.
   */
  public synchronized void set(@Nullable PagingController<Key> bound) {
    this.proxied = bound;
  }
}
