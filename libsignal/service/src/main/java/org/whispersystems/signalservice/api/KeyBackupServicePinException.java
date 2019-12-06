package org.whispersystems.signalservice.api;

import org.whispersystems.signalservice.internal.contacts.entities.TokenResponse;

public final class KeyBackupServicePinException extends TokenException {

  private final int triesRemaining;

  public KeyBackupServicePinException(TokenResponse nextToken) {
    super(nextToken, false);
    this.triesRemaining = nextToken.getTries();
  }

  public int getTriesRemaining() {
    return triesRemaining;
  }
}
