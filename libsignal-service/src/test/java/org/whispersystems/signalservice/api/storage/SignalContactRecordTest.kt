package org.whispersystems.signalservice.api.storage

import okio.ByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.whispersystems.signalservice.api.push.ServiceId.ACI
import org.whispersystems.signalservice.internal.storage.protos.ContactRecord

class SignalContactRecordTest {

  @Test
  fun contacts_with_same_identity_key_contents_are_equal() {
    val identityKey = ByteArray(32)
    val identityKeyCopy = identityKey.clone()

    val contactA = contactBuilder(ACI_A, E164_A, "a")
      .identityKey(ByteString.of(*identityKey))
      .build()
    val contactB = contactBuilder(ACI_A, E164_A, "a")
      .identityKey(ByteString.of(*identityKeyCopy))
      .build()

    val signalContactA = SignalContactRecord(StorageId.forContact(byteArray(1)), contactA)
    val signalContactB = SignalContactRecord(StorageId.forContact(byteArray(1)), contactB)

    assertEquals(signalContactA, signalContactB)
    assertEquals(signalContactA.hashCode(), signalContactB.hashCode())
  }

  @Test
  fun contacts_with_different_identity_key_contents_are_not_equal() {
    val identityKey = ByteArray(32)
    val identityKeyCopy = identityKey.clone()
    identityKeyCopy[0] = 1

    val contactA = contactBuilder(ACI_A, E164_A, "a")
      .identityKey(ByteString.of(*identityKey))
      .build()
    val contactB = contactBuilder(ACI_A, E164_A, "a")
      .identityKey(ByteString.of(*identityKeyCopy))
      .build()

    val signalContactA = SignalContactRecord(StorageId.forContact(byteArray(1)), contactA)
    val signalContactB = SignalContactRecord(StorageId.forContact(byteArray(1)), contactB)

    assertNotEquals(signalContactA, signalContactB)
    assertNotEquals(signalContactA.hashCode(), signalContactB.hashCode())
  }

  companion object {
    private val ACI_A: ACI = ACI.parseOrThrow("ebef429e-695e-4f51-bcc4-526a60ac68c7")
    private const val E164_A: String = "+16108675309"

    private fun byteArray(a: Int): ByteArray {
      val bytes = ByteArray(4)
      bytes[3] = a.toByte()
      bytes[2] = (a shr 8).toByte()
      bytes[1] = (a shr 16).toByte()
      bytes[0] = (a shr 24).toByte()
      return bytes
    }

    private fun contactBuilder(serviceId: ACI, e164: String, givenName: String): ContactRecord.Builder = ContactRecord.Builder()
      .e164(e164)
      .givenName(givenName)
  }
}
