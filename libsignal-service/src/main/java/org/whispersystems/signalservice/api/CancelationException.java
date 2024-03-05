package org.whispersystems.signalservice.api;

import java.io.IOException;

public class CancelationException extends IOException {
  public CancelationException() {
  }

  public CancelationException(Throwable cause) {
    super(cause);
  }
}
