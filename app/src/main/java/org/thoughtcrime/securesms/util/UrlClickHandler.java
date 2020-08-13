package org.thoughtcrime.securesms.util;

import androidx.annotation.NonNull;

public interface UrlClickHandler {

  /**
   * @return true if you have handled it, false if you want to allow the standard Url handling.
   */
  boolean handleOnClick(@NonNull String url);
}
