/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */
package org.whispersystems.libsignal;

public class NoSessionException extends Exception {
  public NoSessionException(String s) {
    super(s);
  }

  public NoSessionException(Exception nested) {
    super(nested);
  }
}
