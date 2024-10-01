/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.database

import org.signal.core.util.select
import org.signal.ringrtc.CallLinkRootKey
import org.signal.ringrtc.CallLinkState
import org.thoughtcrime.securesms.backup.v2.proto.CallLink
import org.thoughtcrime.securesms.database.CallLinkTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.service.webrtc.links.CallLinkCredentials
import org.thoughtcrime.securesms.service.webrtc.links.CallLinkRoomId
import org.thoughtcrime.securesms.service.webrtc.links.SignalCallLinkState
import java.time.Instant

fun CallLinkTable.getCallLinksForBackup(): CallLinkArchiveExportIterator {
  val cursor = readableDatabase
    .select()
    .from(CallLinkTable.TABLE_NAME)
    .run()

  return CallLinkArchiveExportIterator(cursor)
}

fun CallLinkTable.restoreFromBackup(callLink: CallLink): RecipientId? {
  val rootKey: CallLinkRootKey
  try {
    rootKey = CallLinkRootKey(callLink.rootKey.toByteArray())
  } catch (e: Exception) {
    return null
  }
  return SignalDatabase.callLinks.insertCallLink(
    CallLinkTable.CallLink(
      recipientId = RecipientId.UNKNOWN,
      roomId = CallLinkRoomId.fromCallLinkRootKey(rootKey),
      credentials = CallLinkCredentials(callLink.rootKey.toByteArray(), callLink.adminKey?.toByteArray()),
      state = SignalCallLinkState(
        name = callLink.name,
        restrictions = callLink.restrictions.toLocal(),
        expiration = Instant.ofEpochMilli(callLink.expirationMs)
      ),
      deletionTimestamp = 0L
    )
  )
}

private fun CallLink.Restrictions.toLocal(): CallLinkState.Restrictions {
  return when (this) {
    CallLink.Restrictions.ADMIN_APPROVAL -> CallLinkState.Restrictions.ADMIN_APPROVAL
    CallLink.Restrictions.NONE -> CallLinkState.Restrictions.NONE
    CallLink.Restrictions.UNKNOWN -> CallLinkState.Restrictions.UNKNOWN
  }
}
