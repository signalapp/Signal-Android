package org.whispersystems.signalservice.internal.push

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Jackson parser for the response body from the server explaining a failure.
 * See also [org.whispersystems.signalservice.api.push.exceptions.ExternalServiceFailureException]
 */
data class VerificationCodeFailureResponseBody(
  @JsonProperty val permanentFailure: Boolean,
  @JsonProperty val reason: String
)
