/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */
package org.session.libsignal.exceptions;

public class InvalidKeyException extends Exception {

  public InvalidKeyException() {}

  public InvalidKeyException(String detailMessage) {
    super(detailMessage);
  }

  public InvalidKeyException(Throwable throwable) {
    super(throwable);
  }

  public InvalidKeyException(String detailMessage, Throwable throwable) {
    super(detailMessage, throwable);
  }
}
