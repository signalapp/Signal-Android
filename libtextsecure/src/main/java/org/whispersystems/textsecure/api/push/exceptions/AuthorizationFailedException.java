package org.whispersystems.textsecure.api.push.exceptions;

public class AuthorizationFailedException extends NonSuccessfulResponseCodeException {
  public AuthorizationFailedException(String s) {
    super(s);
  }
}
