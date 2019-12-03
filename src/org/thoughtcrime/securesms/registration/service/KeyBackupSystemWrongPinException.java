package org.thoughtcrime.securesms.registration.service;

import androidx.annotation.NonNull;

import org.whispersystems.signalservice.internal.contacts.entities.TokenResponse;

final class KeyBackupSystemWrongPinException extends Exception {

  private final TokenResponse tokenResponse;

  KeyBackupSystemWrongPinException(@NonNull TokenResponse tokenResponse){
    this.tokenResponse = tokenResponse;
  }

  @NonNull TokenResponse getTokenResponse() {
    return tokenResponse;
  }
}
