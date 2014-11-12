package org.whispersystems.textsecure.api.push.exceptions;


public class RateLimitException extends NonSuccessfulResponseCodeException {
  public RateLimitException(String s) {
    super(s);
  }
}
