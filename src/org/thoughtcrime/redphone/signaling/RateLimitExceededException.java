package org.thoughtcrime.redphone.signaling;


public class RateLimitExceededException extends Throwable {
  public RateLimitExceededException(String s) {
    super(s);
  }
}
