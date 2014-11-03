package org.whispersystems.textsecure.push.exceptions;

import java.io.IOException;

public class PushNetworkException extends IOException {
  public PushNetworkException(Exception exception) {
    super(exception);
  }
}
