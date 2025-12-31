package org.whispersystems.signalservice.api.profiles

import com.fasterxml.jackson.annotation.JsonProperty

class SignalServiceProfileWrite(
  @JsonProperty val version: String,
  @JsonProperty val name: ByteArray,
  @JsonProperty val about: ByteArray,
  @JsonProperty val aboutEmoji: ByteArray,
  @JsonProperty val paymentAddress: ByteArray?,
  @JsonProperty val phoneNumberSharing: ByteArray,
  @JsonProperty val avatar: Boolean,
  @JsonProperty val sameAvatar: Boolean,
  @JsonProperty val commitment: ByteArray,
  @JsonProperty val badgeIds: List<String>
)
