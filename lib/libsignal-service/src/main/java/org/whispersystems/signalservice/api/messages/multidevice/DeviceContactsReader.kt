/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.messages.multidevice

import org.signal.core.models.ServiceId.ACI
import org.signal.core.util.readNBytesOrThrow
import org.signal.core.util.readVarInt32OrNull
import org.signal.core.util.skipNBytesOrThrow
import org.signal.core.util.stream.LimitedInputStream
import org.signal.core.util.stream.NonClosingInputStream
import org.whispersystems.signalservice.internal.push.ContactDetails
import java.io.IOException
import java.io.InputStream

/**
 * Reads contacts written by [DeviceContactsWriter]. Does not close [input].
 *
 * Records that decode but have no address are skipped and counted in [skippedRecords]. A truncated or malformed record fails the read with an [IOException].
 *
 * A contact's avatar stream is readable until the next contact is requested. Whatever is left unread is skipped then.
 */
class DeviceContactsReader(private val input: InputStream) {

  companion object {
    private const val MAX_DETAILS_LENGTH = 1024 * 1024
  }

  var skippedRecords: Int = 0
    private set

  fun contacts(): Sequence<DeviceContact> = sequence {
    while (true) {
      val details = readDetails() ?: break
      val avatar = openAvatar(details)
      val contact = toContact(details, avatar?.avatar)
      if (contact == null) {
        skippedRecords++
      } else {
        yield(contact)
      }
      avatar?.skipRemaining()
    }
  }.constrainOnce()

  private fun readDetails(): ContactDetails? {
    val length = input.readVarInt32OrNull() ?: return null
    if (length !in 0..MAX_DETAILS_LENGTH) {
      throw IOException("Contact details length out of range: $length")
    }

    return ContactDetails.ADAPTER.decode(input.readNBytesOrThrow(length))
  }

  private fun openAvatar(details: ContactDetails): AvatarStream? {
    val avatar = details.avatar ?: return null
    val length = avatar.length?.toLong() ?: return null
    if (length < 0) {
      throw IOException("Avatar length out of range: $length")
    }

    return AvatarStream(input, length, avatar.contentType ?: "image/*")
  }

  private fun toContact(details: ContactDetails, avatar: DeviceContactAvatar?): DeviceContact? {
    val aci = ACI.parseOrNull(details.aci, details.aciBinary)?.takeUnless { it.isUnknown }
    val e164 = details.number
    if (aci == null && e164 == null) {
      return null
    }

    return DeviceContact(
      aci = aci,
      e164 = e164,
      name = details.name,
      avatar = avatar,
      expireTimer = details.expireTimer?.takeIf { it > 0 },
      expireTimerVersion = details.expireTimerVersion?.takeIf { it > 0 },
      inboxPosition = details.inboxPosition
    )
  }

  private class AvatarStream(input: InputStream, length: Long, contentType: String) {
    private val stream = LimitedInputStream(NonClosingInputStream(input), length)

    val avatar = DeviceContactAvatar(stream, length, contentType)

    fun skipRemaining() {
      stream.skipNBytesOrThrow(avatar.length - stream.bytesRead)
    }
  }
}
