package org.whispersystems.textsecure.push;

import java.io.IOException;

public class AuthorizationFailedException extends IOException {
  public AuthorizationFailedException(String s) {
    super(s);
  }
}
