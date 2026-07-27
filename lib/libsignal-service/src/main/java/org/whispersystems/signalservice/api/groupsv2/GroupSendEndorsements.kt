/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.groupsv2

import org.signal.core.models.ServiceId
import org.signal.libsignal.metadata.certificate.SenderCertificate
import org.signal.libsignal.zkgroup.groups.GroupSecretParams
import org.signal.libsignal.zkgroup.groupsend.GroupSendEndorsement
import org.signal.libsignal.zkgroup.groupsend.GroupSendFullToken
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import java.io.IOException
import java.time.Instant

/**
 * Helper container for all data needed to send with group send endorsements.
 */
data class GroupSendEndorsements(
  val expirationMs: Long,
  val endorsements: Map<ServiceId.ACI, GroupSendEndorsement>,
  val sealedSenderCertificate: SenderCertificate,
  val groupSecretParams: GroupSecretParams
) {

  private val expiration: Instant by lazy { Instant.ofEpochMilli(expirationMs) }

  @Throws(IOException::class)
  fun toFullToken(addresses: List<SignalServiceAddress>): GroupSendFullToken {
    val combined = GroupSendEndorsement.combine(
      addresses.map { endorsements[it.serviceId] ?: throw IOException("Missing group send endorsement for a group-send recipient") }
    )
    return combined.toFullToken(groupSecretParams, expiration)
  }

  fun forIndividuals(addresses: List<SignalServiceAddress>): List<GroupSendFullToken?> {
    return addresses
      .map { a -> endorsements[a.serviceId] }
      .map { e -> e?.toFullToken(groupSecretParams, expiration) }
  }
}
