package org.whispersystems.signalservice.internal.push

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

@JsonInclude(JsonInclude.Include.NON_NULL)
data class VerificationSessionMetadataRequestBody(
  @JsonProperty val number: String,
  @JsonProperty val pushToken: String?,
  @JsonProperty val mcc: String?,
  @JsonProperty val mnc: String?
) {
  @JsonProperty
  val pushTokenType: String? = if (pushToken != null) "fcm" else null
}
