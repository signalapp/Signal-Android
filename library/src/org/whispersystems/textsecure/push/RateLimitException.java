package org.whispersystems.textsecure.push;


public class RateLimitException extends Exception {
  public RateLimitException(String s) {
    super(s);
  }
}
