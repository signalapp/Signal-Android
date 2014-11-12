package org.whispersystems.textsecure.util;

public interface FutureTaskListener<V> {
  public void onSuccess(V result);
  public void onFailure(Throwable error);
}
