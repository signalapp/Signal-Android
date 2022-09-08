package org.whispersystems.signalservice.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * JSON POJO that represents the returned ACI from a call to
 * /v1/account/username/[username]
 */
class GetAciByUsernameResponse {
  @JsonProperty
  private String uuid;

  GetAciByUsernameResponse() {}

  String getUuid() {
    return uuid;
  }
}
