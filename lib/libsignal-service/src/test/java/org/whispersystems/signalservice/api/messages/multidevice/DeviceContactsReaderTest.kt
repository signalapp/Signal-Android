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
import org.junit.Test
import org.signal.core.models.ServiceId.ACI
import org.whispersystems.signalservice.internal.push.ContactDetails
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.UUID

class DeviceContactsReaderTest {

  // region Round trips through DeviceContactsWriter

  @Test
  fun `round trip - reads multiple contacts in order`() {
    val first = DeviceContact(aci = ACI.from(UUID.randomUUID()), e164 = "+1404555555", name = "Teal'c")
    val second = DeviceContact(aci = ACI.from(UUID.randomUUID()), e164 = "+1444555555", name = "Bra'tac")

    val contacts = readAll(write(first, second))

    assertThat(contacts).hasSize(2)
    assertThat(contacts[0].aci).isEqualTo(first.aci)
    assertThat(contacts[0].e164).isEqualTo("+1404555555")
    assertThat(contacts[0].name).isEqualTo("Teal'c")
    assertThat(contacts[1].aci).isEqualTo(second.aci)
    assertThat(contacts[1].e164).isEqualTo("+1444555555")
    assertThat(contacts[1].name).isEqualTo("Bra'tac")
  }

  @Test
  fun `round trip - preserves every field`() {
    val aci = ACI.from(UUID.randomUUID())
    val avatarData = byteArrayOf(1, 2, 3, 4, 5)
    val contact = DeviceContact(
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
    val contacts = readAll(write(DeviceContact(e164 = "+1404555555", name = "Teal'c")))

    assertThat(contacts).hasSize(1)
    assertThat(contacts[0].aci).isNull()
    assertThat(contacts[0].e164).isEqualTo("+1404555555")
    assertThat(contacts[0].name).isEqualTo("Teal'c")
  }

  @Test
  fun `round trip - contact with only an aci`() {
    val aci = ACI.from(UUID.randomUUID())

    val contacts = readAll(write(DeviceContact(aci = aci)))

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
    val contacts = readAll(write(DeviceContact(aci = ACI.from(UUID.randomUUID()), expireTimer = 0, expireTimerVersion = 0, inboxPosition = 0)))

    assertThat(contacts).hasSize(1)
    assertThat(contacts[0].expireTimer).isNull()
    assertThat(contacts[0].expireTimerVersion).isNull()
    assertThat(contacts[0].inboxPosition).isEqualTo(0)
  }

  @Test
  fun `round trip - avatars of differing sizes stay aligned with their contacts`() {
    val big = ByteArray(10_000) { it.toByte() }
    val small = byteArrayOf(9, 8, 7)
    val first = DeviceContact(aci = ACI.from(UUID.randomUUID()), name = "first", avatar = DeviceContactAvatar(ByteArrayInputStream(big), big.size.toLong(), "image/*"))
    val second = DeviceContact(aci = ACI.from(UUID.randomUUID()), name = "second")
    val third = DeviceContact(aci = ACI.from(UUID.randomUUID()), name = "third", avatar = DeviceContactAvatar(ByteArrayInputStream(small), small.size.toLong(), "image/*"))

    val contacts = readAll(write(first, second, third))

    assertThat(contacts.map { it.name }).isEqualTo(listOf("first", "second", "third"))
    assertThat(contacts[0].avatar!!.bytes).isEqualTo(big.toList())
    assertThat(contacts[1].avatar).isNull()
    assertThat(contacts[2].avatar!!.bytes).isEqualTo(small.toList())
  }

  @Test
  fun `round trip - closing an avatar stream does not close the contacts stream`() {
    val avatarData = byteArrayOf(1, 2, 3, 4, 5)
    val first = DeviceContact(aci = ACI.from(UUID.randomUUID()), e164 = "+1404555555", name = "Teal'c", avatar = DeviceContactAvatar(ByteArrayInputStream(avatarData), avatarData.size.toLong(), "image/*"))
    val second = DeviceContact(aci = ACI.from(UUID.randomUUID()), e164 = "+1444555555", name = "Bra'tac")

    val contacts = DeviceContactsReader(FailsAfterCloseInputStream(ByteArrayInputStream(write(first, second)))).contacts().iterator()
    val readFirst = contacts.next()

    val avatarStream = readFirst.avatar!!.inputStream
    assertThat(avatarStream.readAllBytes().toList()).isEqualTo(avatarData.toList())
    avatarStream.close()

    val readSecond = contacts.next()

    assertThat(readSecond.aci).isEqualTo(second.aci)
    assertThat(readSecond.name).isEqualTo(second.name)
  }

  @Test
  fun `round trip - skips an unread avatar`() {
    val avatarData = byteArrayOf(1, 2, 3, 4, 5)
    val first = DeviceContact(aci = ACI.from(UUID.randomUUID()), avatar = DeviceContactAvatar(ByteArrayInputStream(avatarData), avatarData.size.toLong(), "image/*"))
    val second = DeviceContact(aci = ACI.from(UUID.randomUUID()), name = "Bra'tac")

    val contacts = DeviceContactsReader(ByteArrayInputStream(write(first, second))).contacts().iterator()
    contacts.next()
    val readSecond = contacts.next()

    assertThat(readSecond.aci).isEqualTo(second.aci)
    assertThat(readSecond.name).isEqualTo(second.name)
  }

  @Test
  fun `round trip - skips the rest of a partially read avatar`() {
    val avatarData = byteArrayOf(1, 2, 3, 4, 5)
    val first = DeviceContact(aci = ACI.from(UUID.randomUUID()), avatar = DeviceContactAvatar(ByteArrayInputStream(avatarData), avatarData.size.toLong(), "image/*"))
    val second = DeviceContact(aci = ACI.from(UUID.randomUUID()), name = "Bra'tac")

    val contacts = DeviceContactsReader(ByteArrayInputStream(write(first, second))).contacts().iterator()
    val readFirst = contacts.next()
    assertThat(readFirst.avatar!!.inputStream.readNBytes(2).toList()).isEqualTo(listOf<Byte>(1, 2))

    val readSecond = contacts.next()

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
  fun `wire - an all-zero aci is treated as absent and falls back to the e164`() {
    val contacts = readAll(record(ContactDetails(aciBinary = ACI.UNKNOWN.toByteString(), number = "+1404555555")))

    assertThat(contacts).hasSize(1)
    assertThat(contacts[0].aci).isNull()
    assertThat(contacts[0].e164).isEqualTo("+1404555555")
  }

  @Test
  fun `wire - a contact with no address is skipped and counted`() {
    val reader = DeviceContactsReader(ByteArrayInputStream(record(ContactDetails(name = "nobody"))))

    assertThat(reader.contacts().toList()).isEmpty()
    assertThat(reader.skippedRecords).isEqualTo(1)
  }

  @Test
  fun `wire - a contact with an unparseable aci and no e164 is skipped and counted`() {
    val reader = DeviceContactsReader(ByteArrayInputStream(record(ContactDetails(aci = "not-a-uuid", name = "nobody"))))

    assertThat(reader.contacts().toList()).isEmpty()
    assertThat(reader.skippedRecords).isEqualTo(1)
  }

  @Test
  fun `wire - a skipped contact's avatar is skipped with it`() {
    val skipped = record(ContactDetails(name = "nobody", avatar = ContactDetails.Avatar(length = 3)), byteArrayOf(1, 2, 3))
    val good = record(ContactDetails(aciBinary = randomAciBytes(), name = "good"))
    val reader = DeviceContactsReader(ByteArrayInputStream(skipped + good))

    val contacts = reader.contacts().toList()

    assertThat(contacts.map { it.name }).isEqualTo(listOf("good"))
    assertThat(reader.skippedRecords).isEqualTo(1)
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
  fun `malformed - a varint truncated by end of input fails the read`() {
    val contact = ContactDetails(aciBinary = randomAciBytes(), name = "Teal'c")
    val truncatedVarint = byteArrayOf(0x80.toByte())

    assertFailure { readAll(record(contact) + truncatedVarint) }
      .isInstanceOf(IOException::class)
  }

  @Test
  fun `malformed - contact details truncated by end of input fail the read`() {
    val encoded = ContactDetails(aciBinary = randomAciBytes(), name = "Teal'c").encode()

    assertFailure { readAll(varint(encoded.size) + encoded.copyOf(encoded.size - 1)) }
      .isInstanceOf(IOException::class)
  }

  @Test
  fun `malformed - an avatar truncated by end of input fails the read after the avatar is consumed`() {
    assertFailure { readAll(record(ContactDetails(aciBinary = randomAciBytes(), avatar = ContactDetails.Avatar(length = 5)), byteArrayOf(1, 2))) }
      .isInstanceOf(IOException::class)
  }

  @Test
  fun `malformed - an avatar truncated by end of input fails the read even if left unread`() {
    val contacts = DeviceContactsReader(ByteArrayInputStream(record(ContactDetails(aciBinary = randomAciBytes(), avatar = ContactDetails.Avatar(length = 5)), byteArrayOf(1, 2)))).contacts().iterator()
    contacts.next()

    assertFailure { contacts.hasNext() }
      .isInstanceOf(IOException::class)
  }

  @Test
  fun `malformed - an out of range contact details length fails the read`() {
    val encoded = ContactDetails(aciBinary = randomAciBytes(), name = "Teal'c").encode()

    for (length in listOf(-1, Int.MIN_VALUE, Int.MAX_VALUE, 1024 * 1024 + 1)) {
      assertFailure { readAll(varint(length) + encoded) }
        .isInstanceOf(IOException::class)
    }
  }

  @Test
  fun `malformed - an avatar length above the int range fails the read`() {
    assertFailure { readAll(record(ContactDetails(aciBinary = randomAciBytes(), avatar = ContactDetails.Avatar(length = -1)))) }
      .isInstanceOf(IOException::class)
  }

  @Test
  fun `malformed - a record with no address does not stop later good records`() {
    val good = record(ContactDetails(aciBinary = randomAciBytes(), name = "good"))
    val bad = record(ContactDetails(name = "no address"))
    val reader = DeviceContactsReader(ByteArrayInputStream(good + bad + good))

    val contacts = reader.contacts().toList()

    assertThat(contacts.map { it.name }).isEqualTo(listOf("good", "good"))
    assertThat(reader.skippedRecords).isEqualTo(1)
  }

  @Test
  fun `malformed - a record that does not decode stops the read before later good records`() {
    val good = record(ContactDetails(aciBinary = randomAciBytes(), name = "good"))
    val garbage = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
    val contacts = DeviceContactsReader(ByteArrayInputStream(good + varint(garbage.size) + garbage + good)).contacts().iterator()

    assertThat(contacts.next().name).isEqualTo("good")
    assertFailure { contacts.hasNext() }.isInstanceOf(IOException::class)
  }

  @Test
  fun `write - the avatar length on the wire is the byte count actually read, not the declared length`() {
    val avatar = DeviceContactAvatar(ByteArrayInputStream(byteArrayOf(1, 2, 3)), 5, "image/png")

    val read = readAll(write(DeviceContact(e164 = "+1404555555", avatar = avatar))).single()

    assertThat(read.avatar!!.length).isEqualTo(3L)
    assertThat(read.avatar.bytes).isEqualTo(listOf<Byte>(1, 2, 3))
  }

  @Test
  fun `write - an empty avatar is omitted and its stream is still closed`() {
    val stream = ClosableTrackingInputStream(ByteArrayInputStream(ByteArray(0)))
    val avatar = DeviceContactAvatar(stream, 0, "image/png")

    val read = readAll(write(DeviceContact(e164 = "+1404555555", avatar = avatar))).single()

    assertThat(read.avatar).isNull()
    assertThat(stream.closed).isEqualTo(true)
  }

  // endregion

  // region Helpers

  /** Reads every contact from [bytes], draining each avatar as it goes. */
  private fun readAll(bytes: ByteArray): List<ReadContact> {
    return DeviceContactsReader(ByteArrayInputStream(bytes))
      .contacts()
      .map { contact ->
        ReadContact(
          aci = contact.aci,
          e164 = contact.e164,
          name = contact.name,
          avatar = contact.avatar?.let { ReadAvatar(it.inputStream.readAllBytes().toList(), it.length, it.contentType) },
          expireTimer = contact.expireTimer,
          expireTimerVersion = contact.expireTimerVersion,
          inboxPosition = contact.inboxPosition
        )
      }
      .toList()
  }

  /** Serializes [contacts] with [DeviceContactsWriter]. */
  private fun write(vararg contacts: DeviceContact): ByteArray {
    val bytes = ByteArrayOutputStream()
    val output = DeviceContactsWriter(bytes)
    contacts.forEach { output.write(it) }
    output.close()
    return bytes.toByteArray()
  }

  /** Builds one record by hand: varint length prefix, encoded [details], then the raw [avatar] bytes. */
  private fun record(details: ContactDetails, avatar: ByteArray = ByteArray(0)): ByteArray {
    val encoded = details.encode()
    return varint(encoded.size) + encoded + avatar
  }

  /** Standard protobuf base-128 varint, written independently of [DeviceContactsWriter]. */
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

  private class ClosableTrackingInputStream(wrapped: InputStream) : FilterInputStream(wrapped) {
    var closed = false
      private set

    override fun close() {
      closed = true
      super.close()
    }
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
