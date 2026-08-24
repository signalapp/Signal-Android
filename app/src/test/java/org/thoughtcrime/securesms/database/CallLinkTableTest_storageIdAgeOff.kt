/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database

import android.app.Application
import assertk.assertThat
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.service.webrtc.links.CallLinkCredentials
import org.thoughtcrime.securesms.service.webrtc.links.CallLinkRoomId
import org.thoughtcrime.securesms.service.webrtc.links.SignalCallLinkState
import org.thoughtcrime.securesms.testutil.RecipientTestRule
import org.whispersystems.signalservice.api.storage.StorageId
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class CallLinkTableTest_storageIdAgeOff {

  @get:Rule
  val recipients = RecipientTestRule()

  @Test
  fun `given call links deleted long ago and recently, when I age off storage ids, then I expect only the old one to lose its storage id`() {
    val old = insertCallLink(rootKey = 1, deletedAt = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(46))
    val recent = insertCallLink(rootKey = 2, deletedAt = System.currentTimeMillis())

    SignalDatabase.callLinks.removeStorageIdsFromOldDeletedCallLinks(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(45))

    assertThat(storageIdOf(old)).isNull()
    assertThat(storageIdOf(recent)).isNotNull()
  }

  @Test
  fun `given an old deleted call link and a live call link, when I age off storage ids, then I expect only the deleted one to lose its storage id`() {
    val deleted = insertCallLink(rootKey = 1, deletedAt = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(46))
    val live = insertCallLink(rootKey = 2)

    SignalDatabase.callLinks.removeStorageIdsFromOldDeletedCallLinks(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(45))

    assertThat(storageIdOf(deleted)).isNull()
    assertThat(storageIdOf(live)).isNotNull()
  }

  @Test
  fun `given a deleted call link and a live call link whose storage ids are local only, then I expect only the deleted one to lose its storage id`() {
    val deleted = insertCallLink(rootKey = 1, deletedAt = System.currentTimeMillis())
    val live = insertCallLink(rootKey = 2)

    SignalDatabase.callLinks.removeStorageIdsFromLocalOnlyDeletedCallLinks(listOf(storageIdOf(deleted)!!, storageIdOf(live)!!))

    assertThat(storageIdOf(deleted)).isNull()
    assertThat(storageIdOf(live)).isNotNull()
  }

  @Test
  fun `given a call link deleted one minute ago, when its storage id is local only, then I expect it to lose its storage id`() {
    val deleted = insertCallLink(rootKey = 1, deletedAt = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(1))

    SignalDatabase.callLinks.removeStorageIdsFromLocalOnlyDeletedCallLinks(listOf(storageIdOf(deleted)!!))

    assertThat(storageIdOf(deleted)).isNull()
  }

  @Test
  fun `given two deleted call links, when only one storage id is local only, then I expect only that one to lose its storage id`() {
    val localOnly = insertCallLink(rootKey = 1, deletedAt = System.currentTimeMillis())
    val stillRemote = insertCallLink(rootKey = 2, deletedAt = System.currentTimeMillis())

    SignalDatabase.callLinks.removeStorageIdsFromLocalOnlyDeletedCallLinks(listOf(storageIdOf(localOnly)!!))

    assertThat(storageIdOf(localOnly)).isNull()
    assertThat(storageIdOf(stillRemote)).isNotNull()
  }

  private fun storageIdOf(recipientId: RecipientId): StorageId? {
    return SignalDatabase.recipients.getContactStorageSyncIdsMap()[recipientId]
  }

  private fun insertCallLink(rootKey: Int, deletedAt: Long = 0L): RecipientId {
    val rootKeyBytes = ByteArray(16) { rootKey.toByte() }

    return SignalDatabase.callLinks.insertCallLink(
      callLink = CallLinkTable.CallLink(
        recipientId = RecipientId.UNKNOWN,
        roomId = CallLinkRoomId.fromBytes(rootKeyBytes),
        credentials = CallLinkCredentials(
          linkKeyBytes = rootKeyBytes,
          adminPassBytes = if (deletedAt > 0) null else ByteArray(16) { 9 }
        ),
        state = SignalCallLinkState(),
        deletionTimestamp = deletedAt
      ),
      deletionTimestamp = deletedAt
    )
  }
}
