/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.groupsv2

import org.signal.libsignal.zkgroup.groupsend.GroupSendEndorsementsResponse
import org.signal.storageservice.protos.groups.local.DecryptedGroup

/**
 * Decrypted response from server operations that includes our global group state and
 * our specific-to-us group send endorsements.
 */
class DecryptedGroupResponse(
  val group: DecryptedGroup,
  val groupSendEndorsementsResponse: GroupSendEndorsementsResponse?
)
