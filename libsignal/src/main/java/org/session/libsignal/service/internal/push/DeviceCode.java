package org.session.libsignal.service.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DeviceCode {

  @JsonProperty
  private String verificationCode;

  public String getVerificationCode() {
    return verificationCode;
  }
}
