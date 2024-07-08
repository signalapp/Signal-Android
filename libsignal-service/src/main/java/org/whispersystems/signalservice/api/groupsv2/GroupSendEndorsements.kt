/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.groupsv2

import org.signal.libsignal.metadata.certificate.SenderCertificate
import org.signal.libsignal.zkgroup.groups.GroupSecretParams
import org.signal.libsignal.zkgroup.groupsend.GroupSendEndorsement
import org.signal.libsignal.zkgroup.groupsend.GroupSendFullToken
import org.whispersystems.signalservice.api.push.ServiceId
import org.whispersystems.signalservice.api.push.SignalServiceAddress
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
  private val combinedEndorsement: GroupSendEndorsement by lazy { GroupSendEndorsement.combine(endorsements.values) }

  fun serialize(): ByteArray {
    return combinedEndorsement.toFullToken(groupSecretParams, expiration).serialize()
  }

  fun forIndividuals(addresses: List<SignalServiceAddress>): List<GroupSendFullToken?> {
    return addresses
      .map { a -> endorsements[a.serviceId] }
      .map { e -> e?.toFullToken(groupSecretParams, expiration) }
  }
}
