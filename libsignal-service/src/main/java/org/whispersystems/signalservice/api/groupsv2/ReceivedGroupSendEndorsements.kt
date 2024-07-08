/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.groupsv2

import org.signal.libsignal.zkgroup.groupsend.GroupSendEndorsement
import org.signal.libsignal.zkgroup.groupsend.GroupSendEndorsementsResponse
import org.whispersystems.signalservice.api.push.ServiceId
import java.time.Instant

/**
 * Group send endorsement data received from the server.
 */
data class ReceivedGroupSendEndorsements(
  val expirationMs: Long,
  val endorsements: Map<ServiceId.ACI, GroupSendEndorsement>
) {
  constructor(
    expiration: Instant,
    members: List<ServiceId.ACI>,
    receivedEndorsements: GroupSendEndorsementsResponse.ReceivedEndorsements
  ) : this(
    expirationMs = expiration.toEpochMilli(),
    endorsements = members.zip(receivedEndorsements.endorsements).toMap()
  )
}
