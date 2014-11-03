package org.whispersystems.textsecure.push.exceptions;


import org.whispersystems.textsecure.push.exceptions.NonSuccessfulResponseCodeException;

public class RateLimitException extends NonSuccessfulResponseCodeException {
  public RateLimitException(String s) {
    super(s);
  }
}
