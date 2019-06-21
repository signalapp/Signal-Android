package org.thoughtcrime.securesms.database.documents;

import java.util.List;

public interface Document<T> {

  int size();
  List<T> getList();

}
