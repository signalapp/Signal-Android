package org.thoughtcrime.securesms.storage

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.internal.configuration.plugins.Plugins
import org.mockito.internal.junit.JUnitRule
import org.mockito.junit.MockitoRule
import org.mockito.quality.Strictness
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.dependencies.MockApplicationDependencyProvider
import org.thoughtcrime.securesms.keyvalue.AccountValues
import org.thoughtcrime.securesms.keyvalue.KeyValueDataSet
import org.thoughtcrime.securesms.keyvalue.KeyValueStore
import org.thoughtcrime.securesms.keyvalue.MockKeyValuePersistentStorage
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.testutil.EmptyLogger
import org.thoughtcrime.securesms.util.RemoteConfig
import org.whispersystems.signalservice.api.push.ServiceId.ACI
import org.whispersystems.signalservice.api.push.ServiceId.PNI
import org.whispersystems.signalservice.api.storage.SignalContactRecord
import org.whispersystems.signalservice.api.storage.StorageId
import org.whispersystems.signalservice.internal.storage.protos.ContactRecord
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ContactRecordProcessorTest {

  @Rule
  @JvmField
  val mockitoRule: MockitoRule = JUnitRule(Plugins.getMockitoLogger(), Strictness.STRICT_STUBS)

  @Mock
  lateinit var recipientTable: RecipientTable

  @Mock
  lateinit var remoteConfig: MockedStatic<RemoteConfig>

  @Before
  fun setup() {
    val mockAccountValues = mock(AccountValues::class.java)
    Mockito.lenient().`when`(mockAccountValues.isPrimaryDevice).thenReturn(true)
    if (!AppDependencies.isInitialized) {
      AppDependencies.init(ApplicationProvider.getApplicationContext(), MockApplicationDependencyProvider())
    }
    SignalStore.testInject(KeyValueStore(MockKeyValuePersistentStorage.withDataSet(KeyValueDataSet())))
  }

  @Test
  fun `isInvalid, normal, false`() {
    // GIVEN
    val subject = ContactRecordProcessor(ACI_A, PNI_A, E164_A, recipientTable)

    val record = buildRecord(
      record = ContactRecord(
        aci = ACI_B.toString(),
        pni = PNI_B.toStringWithoutPrefix(),
        e164 = E164_B
      )
    )

    // WHEN
    val result = subject.isInvalid(record)

    // THEN
    assertFalse(result)
  }

  @Test
  fun `isInvalid, missing ACI and PNI, true`() {
    // GIVEN
    val subject = ContactRecordProcessor(ACI_A, PNI_A, E164_A, recipientTable)

    val record = buildRecord(
      record = ContactRecord(
        e164 = E164_B
      )
    )

    // WHEN
    val result = subject.isInvalid(record)

    // THEN
    assertTrue(result)
  }

  @Test
  fun `isInvalid, unknown ACI and PNI, true`() {
    // GIVEN
    val subject = ContactRecordProcessor(ACI_A, PNI_A, E164_A, recipientTable)

    val record = buildRecord(
      record = ContactRecord(
        aci = ACI.UNKNOWN.toString(),
        pni = PNI.UNKNOWN.toString(),
        e164 = E164_B
      )
    )

    // WHEN
    val result = subject.isInvalid(record)

    // THEN
    assertTrue(result)
  }

  @Test
  fun `isInvalid, e164 matches self, true`() {
    // GIVEN
    val subject = ContactRecordProcessor(ACI_A, PNI_A, E164_A, recipientTable)

    val record = buildRecord(
      record = ContactRecord(
        aci = ACI_B.toString(),
        e164 = E164_A
      )
    )

    // WHEN
    val result = subject.isInvalid(record)

    // THEN
    assertTrue(result)
  }

  @Test
  fun `isInvalid, aci matches self, true`() {
    // GIVEN
    val subject = ContactRecordProcessor(ACI_A, PNI_A, E164_A, recipientTable)

    val record = buildRecord(
      record = ContactRecord(
        aci = ACI_A.toString()
      )
    )

    // WHEN
    val result = subject.isInvalid(record)

    // THEN
    assertTrue(result)
  }

  @Test
  fun `isInvalid, pni matches self as pni, true`() {
    // GIVEN
    val subject = ContactRecordProcessor(ACI_A, PNI_A, E164_A, recipientTable)

    val record = buildRecord(
      record = ContactRecord(
        aci = ACI_B.toString(),
        pni = PNI_A.toStringWithoutPrefix()
      )
    )

    // WHEN
    val result = subject.isInvalid(record)

    // THEN
    assertTrue(result)
  }

  @Test
  fun `isInvalid, valid E164, true`() {
    // GIVEN
    val subject = ContactRecordProcessor(ACI_A, PNI_A, E164_A, recipientTable)

    val record = buildRecord(
      record = ContactRecord(
        aci = ACI_B.toString(),
        e164 = E164_B
      )
    )

    // WHEN
    val result = subject.isInvalid(record)

    // THEN
    assertFalse(result)
  }

  @Test
  fun `isInvalid, invalid E164 (missing +), true`() {
    // GIVEN
    val subject = ContactRecordProcessor(ACI_A, PNI_A, E164_A, recipientTable)

    val record = buildRecord(
      record = ContactRecord(
        aci = ACI_B.toString(),
        e164 = "15551234567"
      )
    )

    // WHEN
    val result = subject.isInvalid(record)

    // THEN
    assertTrue(result)
  }

  @Test
  fun `isInvalid, invalid E164 (contains letters), true`() {
    // GIVEN
    val subject = ContactRecordProcessor(ACI_A, PNI_A, E164_A, recipientTable)

    val record = buildRecord(
      record = ContactRecord(
        aci = ACI_B.toString(),
        e164 = "+1555ABC4567"
      )
    )

    // WHEN
    val result = subject.isInvalid(record)

    // THEN
    assertTrue(result)
  }

  @Test
  fun `isInvalid, invalid E164 (no numbers), true`() {
    // GIVEN
    val subject = ContactRecordProcessor(ACI_A, PNI_A, E164_A, recipientTable)

    val record = buildRecord(
      record = ContactRecord(
        aci = ACI_B.toString(),
        e164 = "+"
      )
    )

    // WHEN
    val result = subject.isInvalid(record)

    // THEN
    assertTrue(result)
  }

  @Test
  fun `isInvalid, invalid E164 (too many numbers), true`() {
    // GIVEN
    val subject = ContactRecordProcessor(ACI_A, PNI_A, E164_A, recipientTable)

    val record = buildRecord(
      record = ContactRecord(
        aci = ACI_B.toString(),
        e164 = "+12345678901234567890"
      )
    )

    // WHEN
    val result = subject.isInvalid(record)

    // THEN
    assertTrue(result)
  }

  @Test
  fun `isInvalid, invalid E164 (starts with zero), true`() {
    // GIVEN
    val subject = ContactRecordProcessor(ACI_A, PNI_A, E164_A, recipientTable)

    val record = buildRecord(
      record = ContactRecord(
        aci = ACI_B.toString(),
        e164 = "+05551234567"
      )
    )

    // WHEN
    val result = subject.isInvalid(record)

    // THEN
    assertTrue(result)
  }

  @Test
  fun `merge, e164MatchesButPnisDont pnpEnabled, keepLocal`() {
    // GIVEN
    val subject = ContactRecordProcessor(ACI_A, PNI_A, E164_A, recipientTable)

    val local = buildRecord(
      STORAGE_ID_A,
      record = ContactRecord(
        aci = ACI_A.toString(),
        e164 = E164_A,
        pni = PNI_A.toStringWithoutPrefix()
      )
    )

    val remote = buildRecord(
      STORAGE_ID_B,
      record = ContactRecord(
        aci = ACI_A.toString(),
        e164 = E164_A,
        pni = PNI_B.toStringWithoutPrefix()
      )
    )

    // WHEN
    val result = subject.merge(remote, local, TestKeyGenerator(STORAGE_ID_C))

    // THEN
    assertEquals(local.aci, result.aci)
    assertEquals(local.number.get(), result.number.get())
    assertEquals(local.pni.get(), result.pni.get())
  }

  @Test
  fun `merge, pnisMatchButE164sDont pnpEnabled, keepLocal`() {
    // GIVEN
    val subject = ContactRecordProcessor(ACI_A, PNI_A, E164_A, recipientTable)

    val local = buildRecord(
      STORAGE_ID_A,
      record = ContactRecord(
        aci = ACI_A.toString(),
        e164 = E164_A,
        pni = PNI_A.toStringWithoutPrefix()
      )
    )

    val remote = buildRecord(
      STORAGE_ID_B,
      record = ContactRecord(
        aci = ACI_A.toString(),
        e164 = E164_B,
        pni = PNI_A.toStringWithoutPrefix()
      )
    )

    // WHEN
    val result = subject.merge(remote, local, TestKeyGenerator(STORAGE_ID_C))

    // THEN
    assertEquals(local.aci, result.aci)
    assertEquals(local.number.get(), result.number.get())
    assertEquals(local.pni.get(), result.pni.get())
  }

  @Test
  fun `merge, e164AndPniChange pnpEnabled, useRemote`() {
    // GIVEN
    val subject = ContactRecordProcessor(ACI_A, PNI_A, E164_A, recipientTable)

    val local = buildRecord(
      STORAGE_ID_A,
      record = ContactRecord(
        aci = ACI_A.toString(),
        e164 = E164_A,
        pni = PNI_A.toStringWithoutPrefix()
      )
    )

    val remote = buildRecord(
      STORAGE_ID_B,
      record = ContactRecord(
        aci = ACI_A.toString(),
        e164 = E164_B,
        pni = PNI_B.toStringWithoutPrefix()
      )
    )

    // WHEN
    val result = subject.merge(remote, local, TestKeyGenerator(STORAGE_ID_C))

    // THEN
    assertEquals(remote.aci, result.aci)
    assertEquals(remote.number.get(), result.number.get())
    assertEquals(remote.pni.get(), result.pni.get())
  }

  @Test
  fun `merge, nickname change, useRemote`() {
    // GIVEN
    val subject = ContactRecordProcessor(ACI_A, PNI_A, E164_A, recipientTable)

    val local = buildRecord(
      STORAGE_ID_A,
      record = ContactRecord(
        aci = ACI_A.toString(),
        e164 = E164_A
      )
    )

    val remote = buildRecord(
      STORAGE_ID_B,
      record = ContactRecord(
        aci = ACI_A.toString(),
        e164 = E164_A,
        nickname = ContactRecord.Name(given = "Ghost", family = "Spider"),
        note = "Spidey Friend"
      )
    )

    // WHEN
    val result = subject.merge(remote, local, TestKeyGenerator(STORAGE_ID_C))

    // THEN
    assertEquals("Ghost", result.nicknameGivenName.get())
    assertEquals("Spider", result.nicknameFamilyName.get())
    assertEquals("Spidey Friend", result.note.get())
  }

  private fun buildRecord(id: StorageId = STORAGE_ID_A, record: ContactRecord): SignalContactRecord {
    return SignalContactRecord(id, record)
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
