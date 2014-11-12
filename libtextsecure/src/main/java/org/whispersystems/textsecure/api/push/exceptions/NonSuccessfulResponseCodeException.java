package org.whispersystems.textsecure.api.push.exceptions;

import java.io.IOException;

public class NonSuccessfulResponseCodeException extends IOException {

  public NonSuccessfulResponseCodeException() {
    super();
  }

  public NonSuccessfulResponseCodeException(String s) {
    super(s);
  }
}
