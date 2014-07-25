package org.whispersystems.textsecure.push.exceptions;

public class AuthorizationFailedException extends NonSuccessfulResponseCodeException {
  public AuthorizationFailedException(String s) {
    super(s);
  }
}
