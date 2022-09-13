package org.whispersystems.signalservice.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ReserveUsernameResponse {
  @JsonProperty
  private String username;

  @JsonProperty
  private String reservationToken;

  ReserveUsernameResponse() {}

  /**
   * Visible for testing.
   */
  public ReserveUsernameResponse(String username, String reservationToken) {
    this.username         = username;
    this.reservationToken = reservationToken;
  }

  public String getUsername() {
    return username;
  }

  String getReservationToken() {
    return reservationToken;
  }
}
