package org.thoughtcrime.securesms.gcm;


public class RateLimitException extends Exception {
  public RateLimitException(String s) {
    super(s);
  }
}
