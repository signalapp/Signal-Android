package org.signal.paging;


public interface PagingController<Key> {
  void onDataNeededAroundIndex(int aroundIndex);
  void onDataInvalidated();
  void onDataItemChanged(Key key);
  void onDataItemInserted(Key key, int position);
}
