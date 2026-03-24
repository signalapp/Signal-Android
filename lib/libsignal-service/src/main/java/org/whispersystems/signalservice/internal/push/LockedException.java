package org.whispersystems.signalservice.internal.push;


import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;
import org.whispersystems.signalservice.api.svr.Svr3Credentials;

public final class LockedException extends NonSuccessfulResponseCodeException {

  private final int             length;
  private final long            timeRemaining;
  private final AuthCredentials svr2Credentials;
  private final Svr3Credentials svr3Credentials;

  public LockedException(int length, long timeRemaining, AuthCredentials svr2Credentials, Svr3Credentials svr3Credentials) {
    super(423);
    this.length          = length;
    this.timeRemaining   = timeRemaining;
    this.svr2Credentials = svr2Credentials;
    this.svr3Credentials = svr3Credentials;
  }

  public int getLength() {
    return length;
  }

  public long getTimeRemaining() {
    return timeRemaining;
  }

  public AuthCredentials getSvr2Credentials() {
    return svr2Credentials;
  }

  public Svr3Credentials getSvr3Credentials() {
    return svr3Credentials;
  }
}
