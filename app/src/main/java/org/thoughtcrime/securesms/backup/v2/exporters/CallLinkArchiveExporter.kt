/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.database

import android.database.Cursor
import okio.ByteString.Companion.toByteString
import org.signal.ringrtc.CallLinkState
import org.thoughtcrime.securesms.backup.v2.ArchiveRecipient
import org.thoughtcrime.securesms.backup.v2.proto.CallLink
import org.thoughtcrime.securesms.database.CallLinkTable
import java.io.Closeable

/**
 * Provides a nice iterable interface over a [RecipientTable] cursor, converting rows to [BackupRecipient]s.
 * Important: Because this is backed by a cursor, you must close it. It's recommended to use `.use()` or try-with-resources.
 */
class CallLinkArchiveExporter(private val cursor: Cursor) : Iterator<ArchiveRecipient>, Closeable {
  override fun hasNext(): Boolean {
    return cursor.count > 0 && !cursor.isLast
  }

  override fun next(): ArchiveRecipient {
    if (!cursor.moveToNext()) {
      throw NoSuchElementException()
    }

    val callLink = CallLinkTable.CallLinkDeserializer.deserialize(cursor)
    return ArchiveRecipient(
      id = callLink.recipientId.toLong(),
      callLink = CallLink(
        rootKey = callLink.credentials!!.linkKeyBytes.toByteString(),
        adminKey = callLink.credentials.adminPassBytes?.toByteString(),
        name = callLink.state.name,
        expirationMs = try {
          callLink.state.expiration.toEpochMilli()
        } catch (e: ArithmeticException) {
          Long.MAX_VALUE
        },
        restrictions = callLink.state.restrictions.toRemote()
      )
    )
  }

  override fun close() {
    cursor.close()
  }
}

private fun CallLinkState.Restrictions.toRemote(): CallLink.Restrictions {
  return when (this) {
    CallLinkState.Restrictions.ADMIN_APPROVAL -> CallLink.Restrictions.ADMIN_APPROVAL
    CallLinkState.Restrictions.NONE -> CallLink.Restrictions.NONE
    CallLinkState.Restrictions.UNKNOWN -> CallLink.Restrictions.UNKNOWN
  }
}
