package org.session.libsignal.service.internal.push;


import org.session.libsignal.service.api.push.exceptions.NonSuccessfulResponseCodeException;

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
