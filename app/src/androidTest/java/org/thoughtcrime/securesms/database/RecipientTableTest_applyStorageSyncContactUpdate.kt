/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.storage.StorageRecordUpdate
import org.thoughtcrime.securesms.storage.StorageSyncModels
import org.thoughtcrime.securesms.testing.SignalActivityRule
import org.thoughtcrime.securesms.util.MessageTableTestUtils
import org.whispersystems.signalservice.api.storage.SignalContactRecord
import org.whispersystems.signalservice.api.storage.toSignalContactRecord
import org.whispersystems.signalservice.internal.storage.protos.ContactRecord

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
}
