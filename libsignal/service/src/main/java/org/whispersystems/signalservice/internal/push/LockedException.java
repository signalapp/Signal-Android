package org.whispersystems.signalservice.internal.push;


import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;

public final class LockedException extends NonSuccessfulResponseCodeException {

  private final int             length;
  private final long            timeRemaining;
  private final AuthCredentials svr1Credentials;
  private final AuthCredentials svr2Credentials;

  public LockedException(int length, long timeRemaining, AuthCredentials svr1Credentials, AuthCredentials svr2Credentials) {
    super(423);
    this.length          = length;
    this.timeRemaining   = timeRemaining;
    this.svr1Credentials = svr1Credentials;
    this.svr2Credentials = svr2Credentials;
  }

  public int getLength() {
    return length;
  }

  public long getTimeRemaining() {
    return timeRemaining;
  }

  public AuthCredentials getSvr1Credentials() {
    return svr1Credentials;
  }

  public AuthCredentials getSvr2Credentials() {
    return svr2Credentials;
  }
}
