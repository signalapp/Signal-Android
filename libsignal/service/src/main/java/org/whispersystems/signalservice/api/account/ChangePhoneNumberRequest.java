package org.whispersystems.signalservice.api.account;

import com.fasterxml.jackson.annotation.JsonProperty;

public final class ChangePhoneNumberRequest {
  @JsonProperty
  private String number;

  @JsonProperty
  private String code;

  @JsonProperty("reglock")
  private String registrationLock;

  public ChangePhoneNumberRequest(String number, String code, String registrationLock) {
    this.number           = number;
    this.code             = code;
    this.registrationLock = registrationLock;
  }

  public String getNumber() {
    return number;
  }

  public String getCode() {
    return code;
  }

  public String getRegistrationLock() {
    return registrationLock;
  }
}
