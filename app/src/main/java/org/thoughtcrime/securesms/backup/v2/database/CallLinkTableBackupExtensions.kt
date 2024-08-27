/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.database

import android.database.Cursor
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.signal.core.util.select
import org.signal.ringrtc.CallLinkRootKey
import org.signal.ringrtc.CallLinkState
import org.thoughtcrime.securesms.backup.v2.proto.CallLink
import org.thoughtcrime.securesms.database.CallLinkTable
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.service.webrtc.links.CallLinkCredentials
import org.thoughtcrime.securesms.service.webrtc.links.CallLinkRoomId
import org.thoughtcrime.securesms.service.webrtc.links.SignalCallLinkState
import java.io.Closeable
import java.time.Instant

fun CallLinkTable.getCallLinksForBackup(): BackupCallLinkIterator {
  val cursor = readableDatabase
    .select()
    .from(CallLinkTable.TABLE_NAME)
    .run()

  return BackupCallLinkIterator(cursor)
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
      )
    )
  )
}

/**
 * Provides a nice iterable interface over a [RecipientTable] cursor, converting rows to [BackupRecipient]s.
 * Important: Because this is backed by a cursor, you must close it. It's recommended to use `.use()` or try-with-resources.
 */
class BackupCallLinkIterator(private val cursor: Cursor) : Iterator<BackupRecipient>, Closeable {
  override fun hasNext(): Boolean {
    return cursor.count > 0 && !cursor.isLast
  }

  override fun next(): BackupRecipient {
    if (!cursor.moveToNext()) {
      throw NoSuchElementException()
    }

    val callLink = CallLinkTable.CallLinkDeserializer.deserialize(cursor)
    return BackupRecipient(
      id = callLink.recipientId.toLong(),
      callLink = CallLink(
        rootKey = callLink.credentials?.linkKeyBytes?.toByteString() ?: ByteString.EMPTY,
        adminKey = callLink.credentials?.adminPassBytes?.toByteString(),
        name = callLink.state.name,
        expirationMs = try {
          callLink.state.expiration.toEpochMilli()
        } catch (e: ArithmeticException) { Long.MAX_VALUE },
        restrictions = callLink.state.restrictions.toBackup()
      )
    )
  }

  override fun close() {
    cursor.close()
  }
}

private fun CallLinkState.Restrictions.toBackup(): CallLink.Restrictions {
  return when (this) {
    CallLinkState.Restrictions.ADMIN_APPROVAL -> CallLink.Restrictions.ADMIN_APPROVAL
    CallLinkState.Restrictions.NONE -> CallLink.Restrictions.NONE
    CallLinkState.Restrictions.UNKNOWN -> CallLink.Restrictions.UNKNOWN
  }
}

private fun CallLink.Restrictions.toLocal(): CallLinkState.Restrictions {
  return when (this) {
    CallLink.Restrictions.ADMIN_APPROVAL -> CallLinkState.Restrictions.ADMIN_APPROVAL
    CallLink.Restrictions.NONE -> CallLinkState.Restrictions.NONE
    CallLink.Restrictions.UNKNOWN -> CallLinkState.Restrictions.UNKNOWN
  }
}
