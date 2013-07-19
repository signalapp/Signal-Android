package org.whispersystems.textsecure.push;


import java.io.IOException;

public class RateLimitException extends IOException {
  public RateLimitException(String s) {
    super(s);
  }
}
