package org.whispersystems.signalservice.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;

class ConfirmUsernameRequest {
  @JsonProperty
  private String usernameToConfirm;

  @JsonProperty
  private String reservationToken;

  ConfirmUsernameRequest(String usernameToConfirm, String reservationToken) {
    this.usernameToConfirm = usernameToConfirm;
    this.reservationToken  = reservationToken;
  }
}
