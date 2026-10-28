/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.messages.multidevice

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.messageContains
import org.junit.Test
import org.signal.core.models.ServiceId.ACI
import org.whispersystems.signalservice.internal.push.ContactDetails
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.Optional
import java.util.UUID

class DeviceContactsInputStreamTest {

  // region Round trips through DeviceContactsOutputStream

  @Test
  fun `round trip - reads multiple contacts in order`() {
    val first = deviceContact(aci = ACI.from(UUID.randomUUID()), e164 = "+1404555555", name = "Teal'c")
    val second = deviceContact(aci = ACI.from(UUID.randomUUID()), e164 = "+1444555555", name = "Bra'tac")

    val contacts = readAll(write(first, second))

    assertThat(contacts).hasSize(2)
    assertThat(contacts[0].aci).isEqualTo(first.aci.get())
    assertThat(contacts[0].e164).isEqualTo("+1404555555")
    assertThat(contacts[0].name).isEqualTo("Teal'c")
    assertThat(contacts[1].aci).isEqualTo(second.aci.get())
    assertThat(contacts[1].e164).isEqualTo("+1444555555")
    assertThat(contacts[1].name).isEqualTo("Bra'tac")
  }

  @Test
  fun `round trip - preserves every field`() {
    val aci = ACI.from(UUID.randomUUID())
    val avatarData = byteArrayOf(1, 2, 3, 4, 5)
    val contact = deviceContact(
      aci = aci,
      e164 = "+1404555555",
      name = "Teal'c",
      avatar = DeviceContactAvatar(ByteArrayInputStream(avatarData), avatarData.size.toLong(), "image/png"),
      expireTimer = 86400,
      expireTimerVersion = 3,
      inboxPosition = 7
    )

    val contacts = readAll(write(contact))

    assertThat(contacts).hasSize(1)
    val read = contacts[0]
    assertThat(read.aci).isEqualTo(aci)
    assertThat(read.e164).isEqualTo("+1404555555")
    assertThat(read.name).isEqualTo("Teal'c")
    assertThat(read.avatar).isNotNull()
    assertThat(read.avatar!!.bytes).isEqualTo(avatarData.toList())
    assertThat(read.avatar.length).isEqualTo(5L)
    assertThat(read.avatar.contentType).isEqualTo("image/png")
    assertThat(read.expireTimer).isEqualTo(86400)
    assertThat(read.expireTimerVersion).isEqualTo(3)
    assertThat(read.inboxPosition).isEqualTo(7)
  }

  @Test
  fun `round trip - contact with only an e164`() {
    val contacts = readAll(write(deviceContact(e164 = "+1404555555", name = "Teal'c")))

    assertThat(contacts).hasSize(1)
    assertThat(contacts[0].aci).isNull()
    assertThat(contacts[0].e164).isEqualTo("+1404555555")
    assertThat(contacts[0].name).isEqualTo("Teal'c")
  }

  @Test
  fun `round trip - contact with only an aci`() {
    val aci = ACI.from(UUID.randomUUID())

    val contacts = readAll(write(deviceContact(aci = aci)))

    assertThat(contacts).hasSize(1)
    assertThat(contacts[0].aci).isEqualTo(aci)
    assertThat(contacts[0].e164).isNull()
    assertThat(contacts[0].name).isNull()
    assertThat(contacts[0].avatar).isNull()
    assertThat(contacts[0].expireTimer).isNull()
    assertThat(contacts[0].expireTimerVersion).isNull()
    assertThat(contacts[0].inboxPosition).isNull()
  }

  @Test
  fun `round trip - zero expire timer and version read back as absent but zero inbox position is kept`() {
    val contacts = readAll(write(deviceContact(aci = ACI.from(UUID.randomUUID()), expireTimer = 0, expireTimerVersion = 0, inboxPosition = 0)))

    assertThat(contacts).hasSize(1)
    assertThat(contacts[0].expireTimer).isNull()
    assertThat(contacts[0].expireTimerVersion).isNull()
    assertThat(contacts[0].inboxPosition).isEqualTo(0)
  }

  @Test
  fun `round trip - avatars of differing sizes stay aligned with their contacts`() {
    val big = ByteArray(10_000) { it.toByte() }
    val small = byteArrayOf(9, 8, 7)
    val first = deviceContact(aci = ACI.from(UUID.randomUUID()), name = "first", avatar = DeviceContactAvatar(ByteArrayInputStream(big), big.size.toLong(), "image/*"))
    val second = deviceContact(aci = ACI.from(UUID.randomUUID()), name = "second")
    val third = deviceContact(aci = ACI.from(UUID.randomUUID()), name = "third", avatar = DeviceContactAvatar(ByteArrayInputStream(small), small.size.toLong(), "image/*"))

    val contacts = readAll(write(first, second, third))

    assertThat(contacts.map { it.name }).isEqualTo(listOf("first", "second", "third"))
    assertThat(contacts[0].avatar!!.bytes).isEqualTo(big.toList())
    assertThat(contacts[1].avatar).isNull()
    assertThat(contacts[2].avatar!!.bytes).isEqualTo(small.toList())
  }

  @Test
  fun `round trip - closing an avatar stream does not close the contacts stream`() {
    val avatarData = byteArrayOf(1, 2, 3, 4, 5)
    val first = deviceContact(aci = ACI.from(UUID.randomUUID()), e164 = "+1404555555", name = "Teal'c", avatar = DeviceContactAvatar(ByteArrayInputStream(avatarData), avatarData.size.toLong(), "image/*"))
    val second = deviceContact(aci = ACI.from(UUID.randomUUID()), e164 = "+1444555555", name = "Bra'tac")

    val input = DeviceContactsInputStream(FailsAfterCloseInputStream(ByteArrayInputStream(write(first, second))))
    val readFirst = input.read()

    val avatarStream = readFirst.avatar.get().inputStream
    assertThat(avatarStream.readAllBytes().toList()).isEqualTo(avatarData.toList())
    avatarStream.close()

    val readSecond = input.read()

    assertThat(readSecond.aci).isEqualTo(second.aci)
    assertThat(readSecond.name).isEqualTo(second.name)
  }

  @Test
  fun `round trip - skips an unread avatar`() {
    val avatarData = byteArrayOf(1, 2, 3, 4, 5)
    val first = deviceContact(aci = ACI.from(UUID.randomUUID()), avatar = DeviceContactAvatar(ByteArrayInputStream(avatarData), avatarData.size.toLong(), "image/*"))
    val second = deviceContact(aci = ACI.from(UUID.randomUUID()), name = "Bra'tac")

    val input = DeviceContactsInputStream(ByteArrayInputStream(write(first, second)))
    input.read()
    val readSecond = input.read()

    assertThat(readSecond.aci).isEqualTo(second.aci)
    assertThat(readSecond.name).isEqualTo(second.name)
  }

  @Test
  fun `round trip - skips the rest of a partially read avatar`() {
    val avatarData = byteArrayOf(1, 2, 3, 4, 5)
    val first = deviceContact(aci = ACI.from(UUID.randomUUID()), avatar = DeviceContactAvatar(ByteArrayInputStream(avatarData), avatarData.size.toLong(), "image/*"))
    val second = deviceContact(aci = ACI.from(UUID.randomUUID()), name = "Bra'tac")

    val input = DeviceContactsInputStream(ByteArrayInputStream(write(first, second)))
    val readFirst = input.read()
    assertThat(readFirst.avatar.get().inputStream.readNBytes(2).toList()).isEqualTo(listOf<Byte>(1, 2))

    val readSecond = input.read()

    assertThat(readSecond.aci).isEqualTo(second.aci)
    assertThat(readSecond.name).isEqualTo(second.name)
  }

  // endregion

  // region Hand-built bytes

  @Test
  fun `wire - parses a legacy string aci`() {
    val aci = ACI.from(UUID.randomUUID())

    val contacts = readAll(record(ContactDetails(aci = aci.toString())))

    assertThat(contacts).hasSize(1)
    assertThat(contacts[0].aci).isEqualTo(aci)
  }

  @Test
  fun `wire - prefers the binary aci over the string aci`() {
    val binaryAci = ACI.from(UUID.randomUUID())
    val stringAci = ACI.from(UUID.randomUUID())

    val contacts = readAll(record(ContactDetails(aci = stringAci.toString(), aciBinary = binaryAci.toByteString())))

    assertThat(contacts[0].aci).isEqualTo(binaryAci)
  }

  @Test
  fun `wire - an unparseable aci falls back to the e164`() {
    val contacts = readAll(record(ContactDetails(aci = "not-a-uuid", number = "+1404555555")))

    assertThat(contacts).hasSize(1)
    assertThat(contacts[0].aci).isNull()
    assertThat(contacts[0].e164).isEqualTo("+1404555555")
  }

  @Test
  fun `wire - a contact with no address fails the read`() {
    assertFailure { readAll(record(ContactDetails(name = "nobody"))) }
      .isInstanceOf(IOException::class)
      .messageContains("Missing contact address")
  }

  @Test
  fun `wire - a contact with an unparseable aci and no e164 fails the read`() {
    assertFailure { readAll(record(ContactDetails(aci = "not-a-uuid", name = "nobody"))) }
      .isInstanceOf(IOException::class)
      .messageContains("Missing contact address")
  }

  @Test
  fun `wire - zero expire timer is treated as absent but zero inbox position is kept`() {
    val contacts = readAll(record(ContactDetails(aciBinary = randomAciBytes(), expireTimer = 0, expireTimerVersion = 0, inboxPosition = 0)))

    assertThat(contacts[0].expireTimer).isNull()
    assertThat(contacts[0].expireTimerVersion).isNull()
    assertThat(contacts[0].inboxPosition).isEqualTo(0)
  }

  @Test
  fun `wire - positive expire timer and version are kept`() {
    val contacts = readAll(record(ContactDetails(aciBinary = randomAciBytes(), expireTimer = 60, expireTimerVersion = 2)))

    assertThat(contacts[0].expireTimer).isEqualTo(60)
    assertThat(contacts[0].expireTimerVersion).isEqualTo(2)
  }

  @Test
  fun `wire - avatar without a content type defaults to image wildcard`() {
    val avatarData = byteArrayOf(1, 2, 3)

    val contacts = readAll(record(ContactDetails(aciBinary = randomAciBytes(), avatar = ContactDetails.Avatar(length = avatarData.size)), avatarData))

    assertThat(contacts[0].avatar!!.contentType).isEqualTo("image/*")
    assertThat(contacts[0].avatar!!.bytes).isEqualTo(avatarData.toList())
  }

  @Test
  fun `wire - avatar with a content type keeps it`() {
    val avatarData = byteArrayOf(1, 2, 3)

    val contacts = readAll(record(ContactDetails(aciBinary = randomAciBytes(), avatar = ContactDetails.Avatar(contentType = "image/jpeg", length = avatarData.size)), avatarData))

    assertThat(contacts[0].avatar!!.contentType).isEqualTo("image/jpeg")
  }

  @Test
  fun `wire - avatar message without a length means no avatar`() {
    val contacts = readAll(record(ContactDetails(aciBinary = randomAciBytes(), avatar = ContactDetails.Avatar(contentType = "image/jpeg"))))

    assertThat(contacts[0].avatar).isNull()
  }

  @Test
  fun `wire - zero length avatar is present and empty`() {
    val contacts = readAll(record(ContactDetails(aciBinary = randomAciBytes(), avatar = ContactDetails.Avatar(length = 0))))

    assertThat(contacts[0].avatar).isNotNull()
    assertThat(contacts[0].avatar!!.bytes).isEmpty()
  }

  @Test
  fun `wire - empty input yields no contacts`() {
    assertThat(readAll(ByteArray(0))).isEmpty()
  }

  @Test
  fun `wire - record lengths above 127 use multi-byte varints`() {
    val name = "x".repeat(300)

    val contacts = readAll(record(ContactDetails(aciBinary = randomAciBytes(), name = name)))

    assertThat(contacts[0].name).isEqualTo(name)
  }

  @Test
  fun `wire - unknown proto fields are ignored`() {
    // Field 99, varint wire type: tag = (99 << 3) | 0 = 792 = 0x98 0x06, value 1
    val unknownField = byteArrayOf(0x98.toByte(), 0x06, 0x01)
    val encoded = ContactDetails(aciBinary = randomAciBytes(), name = "Teal'c").encode() + unknownField

    val contacts = readAll(varint(encoded.size) + encoded)

    assertThat(contacts[0].name).isEqualTo("Teal'c")
  }

  // endregion

  // region Malformed input

  @Test
  fun `malformed - garbage contact details fail the read with an IOException`() {
    val garbage = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())

    assertFailure { readAll(varint(garbage.size) + garbage) }
      .isInstanceOf(IOException::class)
  }

  @Test
  fun `malformed - a bad record stops the read before later good records`() {
    val good = record(ContactDetails(aciBinary = randomAciBytes(), name = "good"))
    val bad = record(ContactDetails(name = "no address"))

    assertFailure { readAll(good + bad + good) }.isInstanceOf(IOException::class)

    val input = DeviceContactsInputStream(ByteArrayInputStream(good + bad + good))
    assertThat(input.read().name.get()).isEqualTo("good")
    assertFailure { input.read() }.isInstanceOf(IOException::class)
  }

  // endregion

  // region Helpers

  /** Reads every contact from [bytes], draining each avatar as it goes. The only place that touches the reader's API. */
  private fun readAll(bytes: ByteArray): List<ReadContact> {
    val input = DeviceContactsInputStream(ByteArrayInputStream(bytes))
    val contacts = mutableListOf<ReadContact>()

    var contact: DeviceContact? = input.read()
    while (contact != null) {
      contacts += ReadContact(
        aci = contact.aci.orElse(null),
        e164 = contact.e164.orElse(null),
        name = contact.name.orElse(null),
        avatar = contact.avatar.orElse(null)?.let { ReadAvatar(it.inputStream.readAllBytes().toList(), it.length, it.contentType) },
        expireTimer = contact.expirationTimer.orElse(null),
        expireTimerVersion = contact.expirationTimerVersion.orElse(null),
        inboxPosition = contact.inboxPosition.orElse(null)
      )
      contact = input.read()
    }

    return contacts
  }

  /** Serializes [contacts] with [DeviceContactsOutputStream]. */
  private fun write(vararg contacts: DeviceContact): ByteArray {
    val bytes = ByteArrayOutputStream()
    val output = DeviceContactsOutputStream(bytes)
    contacts.forEach { output.write(it) }
    output.close()
    return bytes.toByteArray()
  }

  /** Builds one record by hand: varint length prefix, encoded [details], then the raw [avatar] bytes. */
  private fun record(details: ContactDetails, avatar: ByteArray = ByteArray(0)): ByteArray {
    val encoded = details.encode()
    return varint(encoded.size) + encoded + avatar
  }

  /** Standard protobuf base-128 varint, written independently of [ChunkedOutputStream]. */
  private fun varint(value: Int): ByteArray {
    val out = ByteArrayOutputStream()
    var remaining = value
    while (remaining and 0x7F.inv() != 0) {
      out.write((remaining and 0x7F) or 0x80)
      remaining = remaining ushr 7
    }
    out.write(remaining)
    return out.toByteArray()
  }

  private fun randomAciBytes() = ACI.from(UUID.randomUUID()).toByteString()

  private fun deviceContact(
    aci: ACI? = null,
    e164: String? = null,
    name: String? = null,
    avatar: DeviceContactAvatar? = null,
    expireTimer: Int? = null,
    expireTimerVersion: Int? = null,
    inboxPosition: Int? = null
  ): DeviceContact {
    return DeviceContact(
      Optional.ofNullable(aci),
      Optional.ofNullable(e164),
      Optional.ofNullable(name),
      Optional.ofNullable(avatar),
      Optional.ofNullable(expireTimer),
      Optional.ofNullable(expireTimerVersion),
      Optional.ofNullable(inboxPosition)
    )
  }

  private class FailsAfterCloseInputStream(input: InputStream) : FilterInputStream(input) {
    private var closed = false

    override fun read(): Int {
      if (closed) throw IOException("Stream Closed")
      return super.read()
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
      if (closed) throw IOException("Stream Closed")
      return super.read(b, off, len)
    }

    override fun close() {
      closed = true
      super.close()
    }
  }

  /** A fully materialized contact, independent of the stream's API. */
  private data class ReadContact(
    val aci: ACI?,
    val e164: String?,
    val name: String?,
    val avatar: ReadAvatar?,
    val expireTimer: Int?,
    val expireTimerVersion: Int?,
    val inboxPosition: Int?
  )

  private data class ReadAvatar(val bytes: List<Byte>, val length: Long, val contentType: String)

  // endregion
}
