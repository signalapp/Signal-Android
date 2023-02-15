package org.thoughtcrime.securesms.storage

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.signal.core.util.update
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.Base64
import org.thoughtcrime.securesms.util.FeatureFlags
import org.thoughtcrime.securesms.util.FeatureFlagsAccessor
import org.whispersystems.signalservice.api.push.ACI
import org.whispersystems.signalservice.api.push.PNI
import org.whispersystems.signalservice.api.storage.SignalContactRecord
import org.whispersystems.signalservice.api.storage.StorageId
import org.whispersystems.signalservice.internal.storage.protos.ContactRecord
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ContactRecordProcessorTest {

  @Before
  fun setup() {
    SignalStore.account().setE164(E164_SELF)
    SignalStore.account().setAci(ACI_SELF)
    SignalStore.account().setPni(PNI_SELF)
    FeatureFlagsAccessor.forceValue(FeatureFlags.PHONE_NUMBER_PRIVACY, true)
  }

  @Test
  fun process_splitContact_normalSplit() {
    // GIVEN
    val originalId = SignalDatabase.recipients.getAndPossiblyMerge(ACI_A, PNI_A, E164_A)
    setStorageId(originalId, STORAGE_ID_A)

    val remote1 = buildRecord(STORAGE_ID_B) {
      setServiceId(ACI_A.toString())
      setUnregisteredAtTimestamp(100)
    }

    val remote2 = buildRecord(STORAGE_ID_C) {
      setServiceId(PNI_A.toString())
      setServicePni(PNI_A.toString())
      setServiceE164(E164_A)
    }

    // WHEN
    val subject = ContactRecordProcessor()
    subject.process(listOf(remote1, remote2), StorageSyncHelper.KEY_GENERATOR)

    // THEN
    val byAci: RecipientId = SignalDatabase.recipients.getByServiceId(ACI_A).get()

    val byE164: RecipientId = SignalDatabase.recipients.getByE164(E164_A).get()
    val byPni: RecipientId = SignalDatabase.recipients.getByServiceId(PNI_A).get()

    assertEquals(originalId, byAci)
    assertEquals(byE164, byPni)
    assertNotEquals(byAci, byE164)
  }

  @Test
  fun process_splitContact_doNotSplitIfAciRecordIsRegistered() {
    // GIVEN
    val originalId = SignalDatabase.recipients.getAndPossiblyMerge(ACI_A, PNI_A, E164_A)
    setStorageId(originalId, STORAGE_ID_A)

    val remote1 = buildRecord(STORAGE_ID_B) {
      setServiceId(ACI_A.toString())
      setUnregisteredAtTimestamp(0)
    }

    val remote2 = buildRecord(STORAGE_ID_C) {
      setServiceId(PNI_A.toString())
      setServicePni(PNI_A.toString())
      setServiceE164(E164_A)
    }

    // WHEN
    val subject = ContactRecordProcessor()
    subject.process(listOf(remote1, remote2), StorageSyncHelper.KEY_GENERATOR)

    // THEN
    val byAci: RecipientId = SignalDatabase.recipients.getByServiceId(ACI_A).get()
    val byE164: RecipientId = SignalDatabase.recipients.getByE164(E164_A).get()
    val byPni: RecipientId = SignalDatabase.recipients.getByPni(PNI_A).get()

    assertEquals(originalId, byAci)
    assertEquals(byE164, byPni)
    assertEquals(byAci, byE164)
  }

  private fun buildRecord(id: StorageId, applyParams: ContactRecord.Builder.() -> ContactRecord.Builder): SignalContactRecord {
    return SignalContactRecord(id, ContactRecord.getDefaultInstance().toBuilder().applyParams().build())
  }

  private fun setStorageId(recipientId: RecipientId, storageId: StorageId) {
    SignalDatabase.rawDatabase
      .update(RecipientTable.TABLE_NAME)
      .values(RecipientTable.STORAGE_SERVICE_ID to Base64.encodeBytes(storageId.raw))
      .where("${RecipientTable.ID} = ?", recipientId)
      .run()
  }

  companion object {
    val ACI_A = ACI.from(UUID.fromString("aaaa0000-5a76-47fa-a98a-7e72c948a82e"))
    val ACI_B = ACI.from(UUID.fromString("bbbb0000-0b60-4a68-9cd9-ed2f8453f9ed"))
    val ACI_SELF = ACI.from(UUID.fromString("77770000-b477-4f35-a824-d92987a63641"))

    val PNI_A = PNI.from(UUID.fromString("aaaa1111-c960-4f6c-8385-671ad2ffb999"))
    val PNI_B = PNI.from(UUID.fromString("bbbb1111-cd55-40bf-adda-c35a85375533"))
    val PNI_SELF = PNI.from(UUID.fromString("77771111-b014-41fb-bf73-05cb2ec52910"))

    const val E164_A = "+12222222222"
    const val E164_B = "+13333333333"
    const val E164_SELF = "+10000000000"

    val STORAGE_ID_A: StorageId = StorageId.forContact(byteArrayOf(1, 2, 3, 4))
    val STORAGE_ID_B: StorageId = StorageId.forContact(byteArrayOf(5, 6, 7, 8))
    val STORAGE_ID_C: StorageId = StorageId.forContact(byteArrayOf(9, 10, 11, 12))
  }
}
