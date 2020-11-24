package org.whispersystems.signalservice.internal.push;


import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;

public class LockedException extends NonSuccessfulResponseCodeException {

  private int  length;
  private long timeRemaining;

  LockedException(int length, long timeRemaining) {
    this.length        = length;
    this.timeRemaining = timeRemaining;
  }

  public int getLength() {
    return length;
  }

  public long getTimeRemaining() {
    return timeRemaining;
  }
}
