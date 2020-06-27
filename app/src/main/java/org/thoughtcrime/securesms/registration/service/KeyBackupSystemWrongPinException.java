package org.thoughtcrime.securesms.registration.service;

import androidx.annotation.NonNull;

import org.whispersystems.signalservice.internal.contacts.entities.TokenResponse;

public final class KeyBackupSystemWrongPinException extends Exception {

  private final TokenResponse tokenResponse;

  public KeyBackupSystemWrongPinException(@NonNull TokenResponse tokenResponse){
    this.tokenResponse = tokenResponse;
  }

  public @NonNull TokenResponse getTokenResponse() {
    return tokenResponse;
  }
}
