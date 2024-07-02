/*
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */
package org.whispersystems.signalservice.api.account

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
class AccountAttributes @JsonCreator constructor(
  @JsonProperty val signalingKey: String?,
  @JsonProperty val registrationId: Int,
  @JsonProperty val voice: Boolean,
  @JsonProperty val video: Boolean,
  @JsonProperty val fetchesMessages: Boolean,
  @JsonProperty val registrationLock: String?,
  @JsonProperty val unidentifiedAccessKey: ByteArray?,
  @JsonProperty val unrestrictedUnidentifiedAccess: Boolean,
  @JsonProperty val discoverableByPhoneNumber: Boolean,
  @JsonProperty val capabilities: Capabilities?,
  @JsonProperty val name: String?,
  @JsonProperty val pniRegistrationId: Int,
  @JsonProperty val recoveryPassword: String?
) {
  constructor(
    signalingKey: String?,
    registrationId: Int,
    fetchesMessages: Boolean,
    registrationLock: String?,
    unidentifiedAccessKey: ByteArray?,
    unrestrictedUnidentifiedAccess: Boolean,
    capabilities: Capabilities?,
    discoverableByPhoneNumber: Boolean,
    name: String?,
    pniRegistrationId: Int,
    recoveryPassword: String?
  ) : this(
    signalingKey = signalingKey,
    registrationId = registrationId,
    voice = true,
    video = true,
    fetchesMessages = fetchesMessages,
    registrationLock = registrationLock,
    unidentifiedAccessKey = unidentifiedAccessKey,
    unrestrictedUnidentifiedAccess = unrestrictedUnidentifiedAccess,
    discoverableByPhoneNumber = discoverableByPhoneNumber,
    capabilities = capabilities,
    name = name,
    pniRegistrationId = pniRegistrationId,
    recoveryPassword = recoveryPassword
  )

  data class Capabilities @JsonCreator constructor(
    @JsonProperty val storage: Boolean,
    @JsonProperty val deleteSync: Boolean
  )
}
