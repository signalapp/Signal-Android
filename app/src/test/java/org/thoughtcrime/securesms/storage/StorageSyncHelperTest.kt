package org.thoughtcrime.securesms.storage

import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import okio.ByteString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.thoughtcrime.securesms.storage.StorageSyncHelper.findIdDifference
import org.thoughtcrime.securesms.storage.StorageSyncHelper.profileKeyChanged
import org.thoughtcrime.securesms.testutil.TestHelpers
import org.thoughtcrime.securesms.util.RemoteConfig
import org.whispersystems.signalservice.api.push.ServiceId.ACI
import org.whispersystems.signalservice.api.push.ServiceId.ACI.Companion.parseOrThrow
import org.whispersystems.signalservice.api.storage.SignalContactRecord
import org.whispersystems.signalservice.api.storage.SignalRecord
import org.whispersystems.signalservice.api.storage.StorageId
import org.whispersystems.signalservice.internal.storage.protos.ContactRecord
import kotlin.time.Duration.Companion.days

class StorageSyncHelperTest {
  @Before
  fun setup() {
    mockkObject(RemoteConfig)
  }

  @After
  fun tearDown() {
    unmockkObject(RemoteConfig)
  }

  @Test
  fun findIdDifference_allOverlap() {
    every { RemoteConfig.messageQueueTime } returns 45.days.inWholeMilliseconds

    val result = findIdDifference(keyListOf(1, 2, 3), keyListOf(1, 2, 3))
    assertTrue(result.localOnlyIds.isEmpty())
    assertTrue(result.remoteOnlyIds.isEmpty())
    assertFalse(result.hasTypeMismatches)
  }

  @Test
  fun findIdDifference_noOverlap() {
    every { RemoteConfig.messageQueueTime } returns 45.days.inWholeMilliseconds

    val result = findIdDifference(keyListOf(1, 2, 3), keyListOf(4, 5, 6))
    TestHelpers.assertContentsEqual(keyListOf(1, 2, 3), result.remoteOnlyIds)
    TestHelpers.assertContentsEqual(keyListOf(4, 5, 6), result.localOnlyIds)
    assertFalse(result.hasTypeMismatches)
  }

  @Test
  fun findIdDifference_someOverlap() {
    every { RemoteConfig.messageQueueTime } returns 45.days.inWholeMilliseconds

    val result = findIdDifference(keyListOf(1, 2, 3), keyListOf(2, 3, 4))
    TestHelpers.assertContentsEqual(keyListOf(1), result.remoteOnlyIds)
    TestHelpers.assertContentsEqual(keyListOf(4), result.localOnlyIds)
    assertFalse(result.hasTypeMismatches)
  }

  @Test
  fun findIdDifference_typeMismatch_allOverlap() {
    every { RemoteConfig.messageQueueTime } returns 45.days.inWholeMilliseconds

    val result = findIdDifference(
      keyListOf(
        mapOf(
          100 to 1,
          200 to 2
        )
      ),
      keyListOf(
        mapOf(
          100 to 1,
          200 to 1
        )
      )
    )

    assertTrue(result.localOnlyIds.isEmpty())
    assertTrue(result.remoteOnlyIds.isEmpty())
    assertTrue(result.hasTypeMismatches)
  }

  @Test
  fun findIdDifference_typeMismatch_someOverlap() {
    every { RemoteConfig.messageQueueTime } returns 45.days.inWholeMilliseconds

    val result = findIdDifference(
      keyListOf(
        mapOf(
          100 to 1,
          200 to 2,
          300 to 1
        )
      ),
      keyListOf(
        mapOf(
          100 to 1,
          200 to 1,
          400 to 1
        )
      )
    )

    TestHelpers.assertContentsEqual(listOf(StorageId.forType(TestHelpers.byteArray(300), 1)), result.remoteOnlyIds)
    TestHelpers.assertContentsEqual(listOf(StorageId.forType(TestHelpers.byteArray(400), 1)), result.localOnlyIds)
    assertTrue(result.hasTypeMismatches)
  }

  @Test
  fun test_ContactUpdate_equals_sameProfileKeys() {
    every { RemoteConfig.messageQueueTime } returns 45.days.inWholeMilliseconds

    val profileKey = ByteArray(32)
    val profileKeyCopy = profileKey.clone()

    val contactA = contactBuilder(ACI_A, E164_A, "a").profileKey(ByteString.of(*profileKey)).build()
    val contactB = contactBuilder(ACI_A, E164_A, "a").profileKey(ByteString.of(*profileKeyCopy)).build()

    val signalContactA = SignalContactRecord(StorageId.forContact(TestHelpers.byteArray(1)), contactA)
    val signalContactB = SignalContactRecord(StorageId.forContact(TestHelpers.byteArray(1)), contactB)

    assertEquals(signalContactA, signalContactB)
    assertEquals(signalContactA.hashCode(), signalContactB.hashCode())

    assertFalse(profileKeyChanged(update(signalContactA, signalContactB)))
  }

  @Test
  fun test_ContactUpdate_equals_differentProfileKeys() {
    every { RemoteConfig.messageQueueTime } returns 45.days.inWholeMilliseconds

    val profileKey = ByteArray(32)
    val profileKeyCopy = profileKey.clone()
    profileKeyCopy[0] = 1

    val contactA = contactBuilder(ACI_A, E164_A, "a").profileKey(ByteString.of(*profileKey)).build()
    val contactB = contactBuilder(ACI_A, E164_A, "a").profileKey(ByteString.of(*profileKeyCopy)).build()

    val signalContactA = SignalContactRecord(StorageId.forContact(TestHelpers.byteArray(1)), contactA)
    val signalContactB = SignalContactRecord(StorageId.forContact(TestHelpers.byteArray(1)), contactB)

    assertNotEquals(signalContactA, signalContactB)
    assertNotEquals(signalContactA.hashCode(), signalContactB.hashCode())

    assertTrue(profileKeyChanged(update(signalContactA, signalContactB)))
  }

  companion object {
    private val ACI_A = parseOrThrow("ebef429e-695e-4f51-bcc4-526a60ac68c7")

    private const val E164_A = "+16108675309"

    @Suppress("SameParameterValue")
    private fun contactBuilder(aci: ACI, e164: String, profileName: String): ContactRecord.Builder {
      return ContactRecord.Builder()
        .aci(aci.toString())
        .e164(e164)
        .givenName(profileName)
    }

    private fun <E : SignalRecord<*>> update(oldRecord: E, newRecord: E): StorageRecordUpdate<E> {
      return StorageRecordUpdate(oldRecord, newRecord)
    }

    private fun keyListOf(vararg vals: Int): List<StorageId> {
      return TestHelpers.byteListOf(*vals).map { StorageId.forType(it, 1) }.toList()
    }

    private fun keyListOf(vals: Map<Int, Int>): List<StorageId> {
      return vals.map { StorageId.forType(TestHelpers.byteArray(it.key), it.value) }.toList()
    }
  }
}
