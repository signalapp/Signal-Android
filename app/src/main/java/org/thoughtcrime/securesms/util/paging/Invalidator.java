package org.thoughtcrime.securesms.util.paging;

import androidx.annotation.NonNull;

public class Invalidator {
  private Runnable callback;

  public synchronized void invalidate() {
    if (callback != null) {
      callback.run();
    }
  }

  public synchronized void observe(@NonNull Runnable callback) {
    this.callback = callback;
  }
}
