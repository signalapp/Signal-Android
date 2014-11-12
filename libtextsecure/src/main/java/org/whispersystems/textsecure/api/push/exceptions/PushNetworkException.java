package org.whispersystems.textsecure.api.push.exceptions;

import java.io.IOException;

public class PushNetworkException extends IOException {
  public PushNetworkException(Exception exception) {
    super(exception);
  }

  public PushNetworkException(String s) {
    super(s);
  }
}
