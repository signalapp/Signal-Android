package org.whispersystems.signalservice.internal.push

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import org.whispersystems.signalservice.internal.util.JsonUtil.UuidDeserializer
import java.util.UUID

/** Response body for setting a username link on the service. */
data class SetUsernameLinkResponseBody(
  @JsonProperty
  @JsonDeserialize(using = UuidDeserializer::class)
  val usernameLinkHandle: UUID
)
