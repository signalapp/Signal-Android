/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.signal.core.models.ServiceId.ACI
import org.signal.core.models.ServiceId.PNI
import org.signal.core.util.nullIfBlank
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.storage.StorageRecordUpdate
import org.thoughtcrime.securesms.storage.StorageSyncModels
import org.thoughtcrime.securesms.testing.SignalActivityRule
import org.thoughtcrime.securesms.util.MessageTableTestUtils
import org.whispersystems.signalservice.api.storage.SignalContactRecord
import org.whispersystems.signalservice.api.storage.signalAci
import org.whispersystems.signalservice.api.storage.signalPni
import org.whispersystems.signalservice.api.storage.toSignalContactRecord
import org.whispersystems.signalservice.internal.storage.protos.ContactRecord
import java.util.UUID

@Suppress("ClassName")
@RunWith(AndroidJUnit4::class)
class RecipientTableTest_applyStorageSyncContactUpdate {
  @get:Rule
  val harness = SignalActivityRule()

  @Test
  fun insertMessageOnVerifiedToDefault() {
    // GIVEN
    val identities = AppDependencies.protocolStore.aci().identities()
    val other = Recipient.resolved(harness.others[0])

    MmsHelper.insert(recipient = other)
    identities.setVerified(other.id, harness.othersKeys[0].publicKey, IdentityTable.VerifiedStatus.VERIFIED)

    val oldRecord: SignalContactRecord = StorageSyncModels.localToRemoteRecord(SignalDatabase.recipients.getRecordForSync(harness.others[0])!!).let { it.proto.contact!!.toSignalContactRecord(it.id) }

    val newProto = oldRecord
      .proto
      .newBuilder()
      .identityState(ContactRecord.IdentityState.DEFAULT)
      .build()
    val newRecord = SignalContactRecord(oldRecord.id, newProto)

    val update = StorageRecordUpdate<SignalContactRecord>(oldRecord, newRecord)

    // WHEN
    val oldVerifiedStatus: IdentityTable.VerifiedStatus = identities.getIdentityRecord(other.id).get().verifiedStatus
    SignalDatabase.recipients.applyStorageSyncContactUpdate(update, true)
    val newVerifiedStatus: IdentityTable.VerifiedStatus = identities.getIdentityRecord(other.id).get().verifiedStatus

    // THEN
    assertThat(oldVerifiedStatus).isEqualTo(IdentityTable.VerifiedStatus.VERIFIED)
    assertThat(newVerifiedStatus).isEqualTo(IdentityTable.VerifiedStatus.DEFAULT)

    val messages = MessageTableTestUtils.getMessages(SignalDatabase.threads.getThreadIdFor(other.id)!!)
    assertThat(messages.first().isIdentityDefault).isTrue()
  }

  @Test
  fun givenAnAlreadySyncedContact_whenMarkedUnregistered_thenItSplitsAndPublishesTheSplit() {
    // GIVEN a registered contact with aci+pni+e164 that is already in storage service (has a storageId)
    val aci = ACI.from(UUID.randomUUID())
    val pni = PNI.from(UUID.randomUUID())
    val e164 = "+13334445555"

    val id = SignalDatabase.recipients.getAndPossiblyMerge(aci, pni, e164)
    SignalDatabase.recipients.markRegistered(id, aci)

    val originalStorageId: ByteArray? = SignalDatabase.recipients.getRecord(id).storageId
    assertThat(originalStorageId).isNotNull()

    // Sanity: the record it currently publishes is whole + registered.
    val before = StorageSyncModels.localToRemoteRecord(SignalDatabase.recipients.getRecordForSync(id)!!).proto.contact!!
    assertThat(before.signalAci).isEqualTo(aci)
    assertThat(before.signalPni).isEqualTo(pni)
    assertThat(before.unregisteredAtTimestamp).isEqualTo(0L)

    // WHEN it is marked unregistered (which strips its pni/e164 and splits it)
    SignalDatabase.recipients.markUnregistered(id)

    // THEN its storageId rotates
    val updatedStorageId: ByteArray? = SignalDatabase.recipients.getRecord(id).storageId
    assertThat(updatedStorageId).isNotNull()
    assertThat(originalStorageId!!.contentEquals(updatedStorageId!!)).isFalse()

    // THEN the published record is now ACI-only + unregistered
    val after = StorageSyncModels.localToRemoteRecord(SignalDatabase.recipients.getRecordForSync(id)!!).proto.contact!!
    assertThat(after.signalAci).isEqualTo(aci)
    assertThat(after.signalPni).isNull()
    assertThat(after.e164.nullIfBlank()).isNull()
    assertThat(after.unregisteredAtTimestamp > 0L).isTrue()

    // THEN the number now lives on a separate PNI-only recipient, so no whole aci+pni+e164 record remains.
    val byPni = SignalDatabase.recipients.getByPni(pni).get()
    assertThat(byPni).isNotEqualTo(id)
    val pniRecord = StorageSyncModels.localToRemoteRecord(SignalDatabase.recipients.getRecordForSync(byPni)!!).proto.contact!!
    assertThat(pniRecord.signalAci).isNull()
    assertThat(pniRecord.signalPni).isEqualTo(pni)
  }
}
