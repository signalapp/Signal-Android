/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */
package org.session.libsignal.exceptions;

public class InvalidMacException extends Exception {

  public InvalidMacException(String detailMessage) {
    super(detailMessage);
  }

  public InvalidMacException(Throwable throwable) {
    super(throwable);
  }
}
