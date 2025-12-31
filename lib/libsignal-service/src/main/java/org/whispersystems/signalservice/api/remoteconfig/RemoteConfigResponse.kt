package org.whispersystems.signalservice.api.remoteconfig

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Response class used in /v2/config. [serverEpochTime] should only be used in REST calls.
 */
data class RemoteConfigResponse(
  @JsonProperty
  val config: Map<String, String> = emptyMap(),
  var serverEpochTime: Long = 0
)
