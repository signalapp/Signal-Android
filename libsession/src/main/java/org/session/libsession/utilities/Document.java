package org.session.libsession.utilities;

import java.util.List;

public interface Document<T> {

  public int size();
  public List<T> getList();

}
