package org.thoughtcrime.securesms.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.internal.configuration.plugins.Plugins
import org.mockito.internal.junit.JUnitRule
import org.mockito.junit.MockitoRule
import org.mockito.quality.Strictness
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.keyvalue.AccountValues
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.testutil.EmptyLogger
import org.thoughtcrime.securesms.util.FeatureFlags
import org.whispersystems.signalservice.api.push.ACI
import org.whispersystems.signalservice.api.push.PNI
import org.whispersystems.signalservice.api.storage.SignalContactRecord
import org.whispersystems.signalservice.api.storage.StorageId
import org.whispersystems.signalservice.internal.storage.protos.ContactRecord
import java.util.UUID

class ContactRecordProcessorTest {

  @Rule
  @JvmField
  val mockitoRule: MockitoRule = JUnitRule(Plugins.getMockitoLogger(), Strictness.STRICT_STUBS)

  @Mock
  lateinit var recipientTable: RecipientTable

  @Mock
  lateinit var featureFlags: MockedStatic<FeatureFlags>

  @Mock
  lateinit var signalStore: MockedStatic<SignalStore>

  @Before
  fun setup() {
    val mockAccountValues = mock(AccountValues::class.java)
    Mockito.lenient().`when`(mockAccountValues.isPrimaryDevice).thenReturn(true)
    signalStore.`when`<AccountValues> { SignalStore.account() }.thenReturn(mockAccountValues)
  }

  @Test
  fun `isInvalid, normal, false`() {
    // GIVEN
    val subject = ContactRecordProcessor(ACI_A, PNI_A, E164_A, recipientTable)

    val record = buildRecord {
      setServiceId(ACI_B.toString())
      setServicePni(PNI_B.toString())
      setServiceE164(E164_B)
    }

    // WHEN
    val result = subject.isInvalid(record)

    // THEN
    assertFalse(result)
  }

  @Test
  fun `isInvalid, missing serviceId, true`() {
    // GIVEN
    val subject = ContactRecordProcessor(ACI_A, PNI_A, E164_A, recipientTable)

    val record = buildRecord {
      setServiceE164(E164_B)
    }

    // WHEN
    val result = subject.isInvalid(record)

    // THEN
    assertTrue(result)
  }

  @Test
  fun `isInvalid, e164 matches self, true`() {
    // GIVEN
    val subject = ContactRecordProcessor(ACI_A, PNI_A, E164_A, recipientTable)

    val record = buildRecord {
      setServiceId(ACI_B.toString())
      setServiceE164(E164_A)
    }

    // WHEN
    val result = subject.isInvalid(record)

    // THEN
    assertTrue(result)
  }

  @Test
  fun `isInvalid, aci matches self, true`() {
    // GIVEN
    val subject = ContactRecordProcessor(ACI_A, PNI_A, E164_A, recipientTable)

    val record = buildRecord {
      setServiceId(ACI_A.toString())
    }

    // WHEN
    val result = subject.isInvalid(record)

    // THEN
    assertTrue(result)
  }

  @Test
  fun `isInvalid, pni matches self as serviceId, true`() {
    // GIVEN
    val subject = ContactRecordProcessor(ACI_A, PNI_A, E164_A, recipientTable)

    val record = buildRecord {
      setServiceId(PNI_A.toString())
    }

    // WHEN
    val result = subject.isInvalid(record)

    // THEN
    assertTrue(result)
  }

  @Test
  fun `isInvalid, pni matches self as pni, true`() {
    // GIVEN
    val subject = ContactRecordProcessor(ACI_A, PNI_A, E164_A, recipientTable)

    val record = buildRecord {
      setServiceId(ACI_B.toString())
      setServicePni(PNI_A.toString())
    }

    // WHEN
    val result = subject.isInvalid(record)

    // THEN
    assertTrue(result)
  }

  @Test
  fun `isInvalid, pniOnly pnpDisabled, true`() {
    // GIVEN
    val subject = ContactRecordProcessor(ACI_A, PNI_A, E164_A, recipientTable)

    featureFlags.`when`<Boolean> { FeatureFlags.phoneNumberPrivacy() }.thenReturn(false)

    val record = buildRecord {
      setServiceId(PNI_B.toString())
      setServicePni(PNI_B.toString())
    }

    // WHEN
    val result = subject.isInvalid(record)

    // THEN
    assertTrue(result)
  }

  @Test
  fun `isInvalid, pniOnly pnpEnabled, false`() {
    // GIVEN
    val subject = ContactRecordProcessor(ACI_A, PNI_A, E164_A, recipientTable)

    featureFlags.`when`<Boolean> { FeatureFlags.phoneNumberPrivacy() }.thenReturn(true)

    val record = buildRecord {
      setServiceId(PNI_B.toString())
      setServicePni(PNI_B.toString())
    }

    // WHEN
    val result = subject.isInvalid(record)

    // THEN
    assertFalse(result)
  }

  @Test
  fun `isInvalid, valid E164, true`() {
    // GIVEN
    val subject = ContactRecordProcessor(ACI_A, PNI_A, E164_A, recipientTable)

    val record = buildRecord {
      setServiceId(ACI_B.toString())
      setServiceE164(E164_B)
    }

    // WHEN
    val result = subject.isInvalid(record)

    // THEN
    assertFalse(result)
  }

  @Test
  fun `isInvalid, invalid E164 (missing +), true`() {
    // GIVEN
    val subject = ContactRecordProcessor(ACI_A, PNI_A, E164_A, recipientTable)

    val record = buildRecord {
      setServiceId(ACI_B.toString())
      setServiceE164("15551234567")
    }

    // WHEN
    val result = subject.isInvalid(record)

    // THEN
    assertTrue(result)
  }

  @Test
  fun `isInvalid, invalid E164 (contains letters), true`() {
    // GIVEN
    val subject = ContactRecordProcessor(ACI_A, PNI_A, E164_A, recipientTable)

    val record = buildRecord {
      setServiceId(ACI_B.toString())
      setServiceE164("+1555ABC4567")
    }

    // WHEN
    val result = subject.isInvalid(record)

    // THEN
    assertTrue(result)
  }

  @Test
  fun `isInvalid, invalid E164 (no numbers), true`() {
    // GIVEN
    val subject = ContactRecordProcessor(ACI_A, PNI_A, E164_A, recipientTable)

    val record = buildRecord {
      setServiceId(ACI_B.toString())
      setServiceE164("+")
    }

    // WHEN
    val result = subject.isInvalid(record)

    // THEN
    assertTrue(result)
  }

  @Test
  fun `isInvalid, invalid E164 (too many numbers), true`() {
    // GIVEN
    val subject = ContactRecordProcessor(ACI_A, PNI_A, E164_A, recipientTable)

    val record = buildRecord {
      setServiceId(ACI_B.toString())
      setServiceE164("+12345678901234567890")
    }

    // WHEN
    val result = subject.isInvalid(record)

    // THEN
    assertTrue(result)
  }

  @Test
  fun `isInvalid, invalid E164 (starts with zero), true`() {
    // GIVEN
    val subject = ContactRecordProcessor(ACI_A, PNI_A, E164_A, recipientTable)

    val record = buildRecord {
      setServiceId(ACI_B.toString())
      setServiceE164("+05551234567")
    }

    // WHEN
    val result = subject.isInvalid(record)

    // THEN
    assertTrue(result)
  }

  @Test
  fun `merge, e164MatchesButPnisDont pnpEnabled, keepLocal`() {
    // GIVEN
    val subject = ContactRecordProcessor(ACI_A, PNI_A, E164_A, recipientTable)

    featureFlags.`when`<Boolean> { FeatureFlags.phoneNumberPrivacy() }.thenReturn(true)

    val local = buildRecord(STORAGE_ID_A) {
      setServiceId(ACI_A.toString())
      setServiceE164(E164_A)
      setServicePni(PNI_A.toString())
    }

    val remote = buildRecord(STORAGE_ID_B) {
      setServiceId(ACI_A.toString())
      setServiceE164(E164_A)
      setServicePni(PNI_B.toString())
    }

    // WHEN
    val result = subject.merge(remote, local, TestKeyGenerator(STORAGE_ID_C))

    // THEN
    assertEquals(local.serviceId, result.serviceId)
    assertEquals(local.number.get(), result.number.get())
    assertEquals(local.pni.get(), result.pni.get())
  }

  @Test
  fun `merge, pnisMatchButE164sDont pnpEnabled, keepLocal`() {
    // GIVEN
    val subject = ContactRecordProcessor(ACI_A, PNI_A, E164_A, recipientTable)

    featureFlags.`when`<Boolean> { FeatureFlags.phoneNumberPrivacy() }.thenReturn(true)

    val local = buildRecord(STORAGE_ID_A) {
      setServiceId(ACI_A.toString())
      setServiceE164(E164_A)
      setServicePni(PNI_A.toString())
    }

    val remote = buildRecord(STORAGE_ID_B) {
      setServiceId(ACI_A.toString())
      setServiceE164(E164_B)
      setServicePni(PNI_A.toString())
    }

    // WHEN
    val result = subject.merge(remote, local, TestKeyGenerator(STORAGE_ID_C))

    // THEN
    assertEquals(local.serviceId, result.serviceId)
    assertEquals(local.number.get(), result.number.get())
    assertEquals(local.pni.get(), result.pni.get())
  }

  @Test
  fun `merge, e164AndPniChange pnpEnabled, useRemote`() {
    // GIVEN
    val subject = ContactRecordProcessor(ACI_A, PNI_A, E164_A, recipientTable)

    featureFlags.`when`<Boolean> { FeatureFlags.phoneNumberPrivacy() }.thenReturn(true)

    val local = buildRecord(STORAGE_ID_A) {
      setServiceId(ACI_A.toString())
      setServiceE164(E164_A)
      setServicePni(PNI_A.toString())
    }

    val remote = buildRecord(STORAGE_ID_B) {
      setServiceId(ACI_A.toString())
      setServiceE164(E164_B)
      setServicePni(PNI_B.toString())
    }

    // WHEN
    val result = subject.merge(remote, local, TestKeyGenerator(STORAGE_ID_C))

    // THEN
    assertEquals(remote.serviceId, result.serviceId)
    assertEquals(remote.number.get(), result.number.get())
    assertEquals(remote.pni.get(), result.pni.get())
  }

  @Test
  fun `merge, pnpDisabled, pniDropped`() {
    // GIVEN
    val subject = ContactRecordProcessor(ACI_A, PNI_A, E164_A, recipientTable)

    featureFlags.`when`<Boolean> { FeatureFlags.phoneNumberPrivacy() }.thenReturn(false)

    val local = buildRecord(STORAGE_ID_A) {
      setServiceId(ACI_A.toString())
      setServiceE164(E164_A)
      setServicePni(PNI_A.toString())
    }

    val remote = buildRecord(STORAGE_ID_B) {
      setServiceId(ACI_A.toString())
      setServiceE164(E164_B)
      setServicePni(PNI_B.toString())
    }

    // WHEN
    val result = subject.merge(remote, local, TestKeyGenerator(STORAGE_ID_C))

    // THEN
    assertEquals(remote.serviceId, result.serviceId)
    assertEquals(remote.number.get(), result.number.get())
    assertEquals(false, result.pni.isPresent)
  }

  private fun buildRecord(id: StorageId = STORAGE_ID_A, applyParams: ContactRecord.Builder.() -> ContactRecord.Builder): SignalContactRecord {
    return SignalContactRecord(id, ContactRecord.getDefaultInstance().toBuilder().applyParams().build())
  }

  private class TestKeyGenerator(private val value: StorageId) : StorageKeyGenerator {
    override fun generate(): ByteArray {
      return value.raw
    }
  }

  companion object {
    val STORAGE_ID_A: StorageId = StorageId.forContact(byteArrayOf(1, 2, 3, 4))
    val STORAGE_ID_B: StorageId = StorageId.forContact(byteArrayOf(5, 6, 7, 8))
    val STORAGE_ID_C: StorageId = StorageId.forContact(byteArrayOf(9, 10, 11, 12))

    val ACI_A = ACI.from(UUID.fromString("3436efbe-5a76-47fa-a98a-7e72c948a82e"))
    val ACI_B = ACI.from(UUID.fromString("8de7f691-0b60-4a68-9cd9-ed2f8453f9ed"))

    val PNI_A = PNI.from(UUID.fromString("154b8d92-c960-4f6c-8385-671ad2ffb999"))
    val PNI_B = PNI.from(UUID.fromString("ba92b1fb-cd55-40bf-adda-c35a85375533"))

    const val E164_A = "+12221234567"
    const val E164_B = "+13331234567"

    @JvmStatic
    @BeforeClass
    fun setUpClass() {
      Log.initialize(EmptyLogger())
    }
  }
}
