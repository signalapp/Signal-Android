package org.thoughtcrime.securesms.storage

import android.app.Application
import io.mockk.every
import io.mockk.mockk
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.util.Hex.fromStringCondensed
import org.signal.libsignal.zkgroup.groups.GroupMasterKey
import org.thoughtcrime.securesms.database.GroupTable
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.keyvalue.AccountValues
import org.thoughtcrime.securesms.testutil.MockSignalStoreRule
import org.whispersystems.signalservice.api.storage.SignalGroupV2Record
import org.whispersystems.signalservice.api.storage.StorageId
import org.whispersystems.signalservice.internal.storage.protos.GroupV2Record
import java.util.Random

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class GroupV2RecordProcessorTest {

  companion object {
    private val MASTER_KEY = GroupMasterKey(fromStringCondensed("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")).serialize().toByteString()
    private val HASH_A = ByteArray(32) { 0xAA.toByte() }
    private val HASH_B = ByteArray(32) { 0xBB.toByte() }

    private val random = Random()

    private fun record(verifiedNameHash: ByteArray?): SignalGroupV2Record {
      val proto = GroupV2Record.Builder()
        .masterKey(MASTER_KEY)
        .verifiedNameHash(verifiedNameHash?.toByteString() ?: ByteString.EMPTY)
        .build()
      return SignalGroupV2Record(StorageId.forGroupV2(randomKey()), proto)
    }

    private fun randomKey(): ByteArray {
      val key = ByteArray(16)
      random.nextBytes(key)
      return key
    }

    private fun keyGenerator(): StorageKeyGenerator {
      return StorageKeyGenerator { randomKey() }
    }
  }

  @get:Rule
  val signalStore = MockSignalStoreRule(relaxed = setOf(AccountValues::class))

  private lateinit var processor: GroupV2RecordProcessor

  @Before
  fun setUp() {
    every { signalStore.account.isPrimaryDevice } returns true

    processor = GroupV2RecordProcessor(mockk<RecipientTable>(relaxed = true), mockk<GroupTable>(relaxed = true))
  }

  @Test
  fun `merge prefers remote hash when remote is non-empty`() {
    val local = record(verifiedNameHash = HASH_A)
    val remote = record(verifiedNameHash = HASH_B)

    val merged = processor.merge(remote, local, keyGenerator())

    assertArrayEquals(HASH_B, merged.proto.verifiedNameHash.toByteArray())
  }

  @Test
  fun `merge keeps local hash when remote hash is empty`() {
    val local = record(verifiedNameHash = HASH_A)
    val remote = record(verifiedNameHash = null)

    val merged = processor.merge(remote, local, keyGenerator())

    assertArrayEquals(HASH_A, merged.proto.verifiedNameHash.toByteArray())
  }

  @Test
  fun `merge takes remote hash when local hash is empty`() {
    val local = record(verifiedNameHash = null)
    val remote = record(verifiedNameHash = HASH_B)

    val merged = processor.merge(remote, local, keyGenerator())

    assertArrayEquals(HASH_B, merged.proto.verifiedNameHash.toByteArray())
  }

  @Test
  fun `merge yields empty hash when both are empty`() {
    val local = record(verifiedNameHash = null)
    val remote = record(verifiedNameHash = null)

    val merged = processor.merge(remote, local, keyGenerator())

    assertTrue(merged.proto.verifiedNameHash.size == 0)
  }

  @Test
  fun `merge returns local instance when local already matches merged`() {
    val local = record(verifiedNameHash = HASH_A)
    val remote = record(verifiedNameHash = null)

    val merged = processor.merge(remote, local, keyGenerator())

    assertEquals(local.id, merged.id)
  }

  @Test
  fun `merge returns remote instance when remote already matches merged`() {
    val local = record(verifiedNameHash = null)
    val remote = record(verifiedNameHash = HASH_B)

    val merged = processor.merge(remote, local, keyGenerator())

    assertEquals(remote.id, merged.id)
  }
}
