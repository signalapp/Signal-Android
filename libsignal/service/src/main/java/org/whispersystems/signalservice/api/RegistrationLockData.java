package org.whispersystems.signalservice.api;

import org.whispersystems.signalservice.internal.contacts.entities.TokenResponse;
import org.whispersystems.signalservice.internal.registrationpin.PinStretcher;

public final class RegistrationLockData {

  private final PinStretcher.MasterKey masterKey;
  private final TokenResponse          tokenResponse;

  RegistrationLockData(PinStretcher.MasterKey masterKey, TokenResponse tokenResponse) {
    this.masterKey     = masterKey;
    this.tokenResponse = tokenResponse;
  }

  public PinStretcher.MasterKey getMasterKey() {
    return masterKey;
  }

  public TokenResponse getTokenResponse() {
    return tokenResponse;
  }

  public int getRemainingTries() {
    return tokenResponse.getTries();
  }
}
