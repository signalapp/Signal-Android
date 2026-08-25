/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.storage

import android.app.Application
import assertk.assertThat
import assertk.assertions.isNotNull
import assertk.assertions.isPresent
import okio.ByteString.Companion.toByteString
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.util.UuidUtil
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.DistributionListId
import org.thoughtcrime.securesms.testutil.RecipientTestRule
import org.whispersystems.signalservice.api.push.DistributionId
import org.whispersystems.signalservice.api.storage.SignalStoryDistributionListRecord
import org.whispersystems.signalservice.api.storage.StorageId
import org.whispersystems.signalservice.internal.storage.protos.StoryDistributionListRecord
import java.util.UUID
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class StoryDistributionListRecordProcessorAgeOffTest {

  @get:Rule
  val recipients = RecipientTestRule()

  private val testSubject = StoryDistributionListRecordProcessor()

  @Test
  fun `given an aged off list tombstone, when the remote manifest still has its id, then I expect a matching record with a fresh storage id`() {
    val deletedAt = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(46)
    val distributionId = DistributionId.from(UUID.randomUUID())
    val listId = insertAgedOffList(distributionId, deletedAt)

    val matching = testSubject.getMatching(remoteTombstone(distributionId, deletedAt)) { StorageSyncHelper.generateKey() }

    assertThat(matching).isPresent()
    assertThat(storageIdOf(listId)).isNotNull()
  }

  private fun insertAgedOffList(distributionId: DistributionId, deletedAt: Long): DistributionListId {
    val listId = SignalDatabase.distributionLists.createList("test", emptyList(), distributionId = distributionId)!!
    SignalDatabase.distributionLists.deleteList(listId, deletedAt)
    SignalDatabase.distributionLists.removeStorageIdsFromOldDeletedLists(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(45))

    return listId
  }

  private fun remoteTombstone(distributionId: DistributionId, deletedAt: Long): SignalStoryDistributionListRecord {
    return SignalStoryDistributionListRecord(
      StorageId.forStoryDistributionList(byteArrayOf(1, 2, 3, 4)),
      StoryDistributionListRecord()
        .newBuilder()
        .identifier(UuidUtil.toByteArray(distributionId.asUuid()).toByteString())
        .deletedAtTimestamp(deletedAt)
        .build()
    )
  }

  private fun storageIdOf(listId: DistributionListId): StorageId? {
    val recipientId = SignalDatabase.distributionLists.getRecipientId(listId)!!
    return SignalDatabase.recipients.getContactStorageSyncIdsMap()[recipientId]
  }
}
