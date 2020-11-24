/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */
package org.whispersystems.libsignal;

import java.util.List;

public class InvalidMessageException extends Exception {

  public InvalidMessageException() {}

  public InvalidMessageException(String detailMessage) {
    super(detailMessage);
  }

  public InvalidMessageException(Throwable throwable) {
    super(throwable);
  }

  public InvalidMessageException(String detailMessage, Throwable throwable) {
    super(detailMessage, throwable);
  }

  public InvalidMessageException(String detailMessage, List<Exception> exceptions) {
    super(detailMessage, exceptions.get(0));
  }
}
