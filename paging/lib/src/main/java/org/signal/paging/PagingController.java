package org.signal.paging;


public interface PagingController {
  void onDataNeededAroundIndex(int aroundIndex);
  void onDataInvalidated();
}
