package org.thoughtcrime.securesms.util;

public interface FutureTaskListener<V> {
  public void onSuccess(V result);
  public void onFailure(Throwable error);
}
