package org.whispersystems.textsecure.api.push.exceptions;

import java.io.IOException;
import java.util.List;

public class UnregisteredUserException extends IOException {

  private final String e164number;

  public UnregisteredUserException(String e164number, Exception exception) {
    super(exception);
    this.e164number = e164number;
  }

  public String getE164Number() {
    return e164number;
  }
}
