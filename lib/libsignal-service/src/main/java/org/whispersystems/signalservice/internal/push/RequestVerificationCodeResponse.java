package org.whispersystems.signalservice.internal.push;


import java.util.Optional;

public final class RequestVerificationCodeResponse {
  private final Optional<String> fcmToken;

  public RequestVerificationCodeResponse(Optional<String> fcmToken) {
    this.fcmToken = fcmToken;
  }

  public Optional<String> getFcmToken() {
    return fcmToken;
  }
}
