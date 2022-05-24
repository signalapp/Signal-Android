package org.signal.paging;


public interface PagingController<Key> {
  int POSITION_END = -1;

  void onDataNeededAroundIndex(int aroundIndex);
  void onDataInvalidated();
  void onDataItemChanged(Key key);
  void onDataItemInserted(Key key, int position);
}
