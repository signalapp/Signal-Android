package org.whispersystems.signalservice.internal.push

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Response object for /v1/accounts/whoami
 */
data class WhoAmIResponse @JsonCreator constructor(
  @JsonProperty("uuid") val aci: String? = null,
  @JsonProperty val pni: String? = null,
  @JsonProperty val number: String,
  @JsonProperty val usernameHash: String? = null,
  @JsonProperty val entitlements: Entitlements? = null
) {
  data class Entitlements @JsonCreator constructor(
    @JsonProperty val badges: List<BadgeEntitlement>? = null,
    @JsonProperty val backup: BackupEntitlement? = null
  )

  data class BadgeEntitlement @JsonCreator constructor(
    @JsonProperty val id: String?,
    @JsonProperty val visible: Boolean?,
    @JsonProperty val expirationSeconds: Long?
  )

  data class BackupEntitlement @JsonCreator constructor(
    @JsonProperty val backupLevel: Long?,
    @JsonProperty val expirationSeconds: Long?
  )
}
