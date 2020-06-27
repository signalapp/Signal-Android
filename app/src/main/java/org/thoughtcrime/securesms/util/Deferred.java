package org.thoughtcrime.securesms.util;

import androidx.annotation.Nullable;

public class Deferred {

  private Runnable deferred;
  private boolean  isDeferred = true;

  public void defer(@Nullable Runnable deferred) {
    this.deferred = deferred;
    executeIfNecessary();
  }

  public void setDeferred(boolean isDeferred) {
    this.isDeferred = isDeferred;
    executeIfNecessary();
  }

  public boolean isDeferred() {
    return isDeferred;
  }

  private void executeIfNecessary() {
    if (deferred != null && !isDeferred) {
      Runnable local = deferred;

      deferred = null;

      local.run();
    }
  }
}
