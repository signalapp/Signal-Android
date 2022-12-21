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
  @JsonProperty val isVoice: Boolean,
  @JsonProperty val isVideo: Boolean,
  @JsonProperty val isFetchesMessages: Boolean,
  @JsonProperty val pin: String?,
  @JsonProperty val registrationLock: String?,
  @JsonProperty val unidentifiedAccessKey: ByteArray?,
  @JsonProperty val isUnrestrictedUnidentifiedAccess: Boolean,
  @JsonProperty val isDiscoverableByPhoneNumber: Boolean,
  @JsonProperty val capabilities: Capabilities?,
  @JsonProperty val name: String?,
  @JsonProperty val pniRegistrationId: Int,
) {
  constructor(
    signalingKey: String?,
    registrationId: Int,
    isFetchesMessages: Boolean,
    pin: String?,
    registrationLock: String?,
    unidentifiedAccessKey: ByteArray?,
    isUnrestrictedUnidentifiedAccess: Boolean,
    capabilities: Capabilities?,
    isDiscoverableByPhoneNumber: Boolean,
    name: String?,
    pniRegistrationId: Int
  ) : this(
    signalingKey = signalingKey,
    registrationId = registrationId,
    isVoice = true,
    isVideo = true,
    isFetchesMessages = isFetchesMessages,
    pin = pin,
    registrationLock = registrationLock,
    unidentifiedAccessKey = unidentifiedAccessKey,
    isUnrestrictedUnidentifiedAccess = isUnrestrictedUnidentifiedAccess,
    isDiscoverableByPhoneNumber = isDiscoverableByPhoneNumber,
    capabilities = capabilities,
    name = name,
    pniRegistrationId = pniRegistrationId
  )

  data class Capabilities @JsonCreator constructor(
    @JsonProperty val isUuid: Boolean,
    @JsonProperty("gv2-3") val isGv2: Boolean,
    @JsonProperty val isStorage: Boolean,
    @JsonProperty("gv1-migration") val isGv1Migration: Boolean,
    @JsonProperty val isSenderKey: Boolean,
    @JsonProperty val isAnnouncementGroup: Boolean,
    @JsonProperty val isChangeNumber: Boolean,
    @JsonProperty val isStories: Boolean,
    @JsonProperty val isGiftBadges: Boolean,
    @JsonProperty val isPnp: Boolean,
    @JsonProperty val paymentActivation: Boolean
  )
}
