/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.messages.multidevice

import org.signal.core.util.writeVarInt32
import org.whispersystems.signalservice.internal.push.ContactDetails
import java.io.IOException
import java.io.OutputStream

/**
 * Writes contacts for a contact sync. Each record is a varint length, a [ContactDetails] proto, then the raw avatar bytes if there is an avatar.
 */
class DeviceContactsWriter(private val out: OutputStream) {

  @Throws(IOException::class)
  fun write(contact: DeviceContact) {
    val avatar = contact.avatar
    val avatarBytes = avatar?.inputStream?.use { it.readBytes() } ?: ByteArray(0)

    val details = ContactDetails(
      aciBinary = contact.aci?.toByteString(),
      number = contact.e164,
      name = contact.name,
      avatar = if (avatar != null && avatarBytes.isNotEmpty()) ContactDetails.Avatar(contentType = avatar.contentType, length = avatarBytes.size) else null,
      expireTimer = contact.expireTimer,
      expireTimerVersion = contact.expireTimerVersion,
      inboxPosition = contact.inboxPosition
    ).encode()

    out.writeVarInt32(details.size)
    out.write(details)
    out.write(avatarBytes)
  }

  @Throws(IOException::class)
  fun close() {
    out.close()
  }
}
