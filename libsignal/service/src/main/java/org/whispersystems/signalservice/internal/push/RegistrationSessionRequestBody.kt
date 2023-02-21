package org.whispersystems.signalservice.internal.push

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import org.whispersystems.signalservice.api.account.AccountAttributes

@JsonInclude(JsonInclude.Include.NON_NULL)
data class RegistrationSessionRequestBody(
  @JsonProperty val sessionId: String? = null,
  @JsonProperty val recoveryPassword: String? = null,
  @JsonProperty val accountAttributes: AccountAttributes,
  @JsonProperty val skipDeviceTransfer: Boolean
)
