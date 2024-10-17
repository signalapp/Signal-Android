/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.exporters

import android.database.Cursor
import okio.ByteString.Companion.toByteString
import org.signal.core.util.Base64
import org.signal.core.util.logging.Log
import org.signal.core.util.requireBoolean
import org.signal.core.util.requireInt
import org.signal.core.util.requireLong
import org.signal.core.util.requireString
import org.thoughtcrime.securesms.backup.v2.ArchiveRecipient
import org.thoughtcrime.securesms.backup.v2.proto.Contact
import org.thoughtcrime.securesms.backup.v2.proto.Self
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.database.RecipientTableCursorUtil
import org.thoughtcrime.securesms.recipients.Recipient
import org.whispersystems.signalservice.api.push.ServiceId
import org.whispersystems.signalservice.api.util.toByteArray
import java.io.Closeable

/**
 * Provides a nice iterable interface over a [RecipientTable] cursor, converting rows to [ArchiveRecipient]s.
 * Important: Because this is backed by a cursor, you must close it. It's recommended to use `.use()` or try-with-resources.
 */
class ContactArchiveExporter(private val cursor: Cursor, private val selfId: Long) : Iterator<ArchiveRecipient?>, Closeable {

  companion object {
    private val TAG = Log.tag(ContactArchiveExporter::class)
  }

  override fun hasNext(): Boolean {
    return cursor.count > 0 && !cursor.isLast
  }

  override fun next(): ArchiveRecipient? {
    if (!cursor.moveToNext()) {
      throw NoSuchElementException()
    }

    val id = cursor.requireLong(RecipientTable.ID)
    if (id == selfId) {
      return ArchiveRecipient(
        id = id,
        self = Self()
      )
    }

    val aci = ServiceId.ACI.Companion.parseOrNull(cursor.requireString(RecipientTable.ACI_COLUMN))
    val pni = ServiceId.PNI.Companion.parseOrNull(cursor.requireString(RecipientTable.PNI_COLUMN))
    val e164 = cursor.requireString(RecipientTable.E164)?.e164ToLong()

    if (aci == null && pni == null && e164 == null) {
      Log.w(TAG, "All identifiers are null! Before parsing, ACI: ${cursor.requireString(RecipientTable.ACI_COLUMN) != null}, PNI: ${cursor.requireString(RecipientTable.PNI_COLUMN) != null},  E164: ${cursor.requireString(RecipientTable.E164) != null}")
      return null
    }

    val contactBuilder = Contact.Builder()
      .aci(aci?.rawUuid?.toByteArray()?.toByteString())
      .pni(pni?.rawUuid?.toByteArray()?.toByteString())
      .username(cursor.requireString(RecipientTable.USERNAME))
      .e164(cursor.requireString(RecipientTable.E164)?.e164ToLong())
      .blocked(cursor.requireBoolean(RecipientTable.BLOCKED))
      .visibility(Recipient.HiddenState.deserialize(cursor.requireInt(RecipientTable.HIDDEN)).toRemote())
      .profileKey(cursor.requireString(RecipientTable.PROFILE_KEY)?.let { Base64.decode(it) }?.toByteString())
      .profileSharing(cursor.requireBoolean(RecipientTable.PROFILE_SHARING))
      .profileGivenName(cursor.requireString(RecipientTable.PROFILE_GIVEN_NAME))
      .profileFamilyName(cursor.requireString(RecipientTable.PROFILE_FAMILY_NAME))
      .hideStory(RecipientTableCursorUtil.getExtras(cursor)?.hideStory() ?: false)

    val registeredState = RecipientTable.RegisteredState.fromId(cursor.requireInt(RecipientTable.REGISTERED))
    if (registeredState == RecipientTable.RegisteredState.REGISTERED) {
      contactBuilder.registered = Contact.Registered()
    } else {
      contactBuilder.notRegistered = Contact.NotRegistered(unregisteredTimestamp = cursor.requireLong(RecipientTable.UNREGISTERED_TIMESTAMP))
    }

    return ArchiveRecipient(
      id = id,
      contact = contactBuilder.build()
    )
  }

  override fun close() {
    cursor.close()
  }
}

private fun Recipient.HiddenState.toRemote(): Contact.Visibility {
  return when (this) {
    Recipient.HiddenState.NOT_HIDDEN -> return Contact.Visibility.VISIBLE
    Recipient.HiddenState.HIDDEN -> return Contact.Visibility.HIDDEN
    Recipient.HiddenState.HIDDEN_MESSAGE_REQUEST -> return Contact.Visibility.HIDDEN_MESSAGE_REQUEST
  }
}

private fun String.e164ToLong(): Long? {
  val fixed = if (this.startsWith("+")) {
    this.substring(1)
  } else {
    this
  }

  return fixed.toLongOrNull()
}
