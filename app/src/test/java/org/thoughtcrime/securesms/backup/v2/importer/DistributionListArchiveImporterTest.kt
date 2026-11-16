/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.importer

import android.app.Application
import assertk.assertThat
import assertk.assertions.isEqualTo
import okio.ByteString.Companion.toByteString
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.archive.proto.DistributionList
import org.signal.archive.proto.DistributionListItem
import org.signal.core.models.backup.MediaRootBackupKey
import org.signal.core.util.UuidUtil
import org.thoughtcrime.securesms.backup.v2.BackupMode
import org.thoughtcrime.securesms.backup.v2.ImportState
import org.thoughtcrime.securesms.database.DistributionListTables
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.testutil.RecipientTestRule
import org.whispersystems.signalservice.api.push.DistributionId
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class DistributionListArchiveImporterTest {

  @get:Rule
  val recipients = RecipientTestRule()

  private val importState = ImportState(MediaRootBackupKey(ByteArray(32)), BackupMode.REMOTE)

  @Before
  fun setUp() {
    // Backup import drops every table before any frame is read.
    SignalDatabase.writableDatabase.execSQL("DELETE FROM ${DistributionListTables.ListTable.TABLE_NAME}")
    SignalDatabase.writableDatabase.execSQL("DELETE FROM ${RecipientTable.TABLE_NAME} WHERE ${RecipientTable.TYPE} = ${RecipientTable.RecipientType.DISTRIBUTION_LIST.id}")
  }

  @Test
  fun `My Story is stored with its distribution id as the name`() {
    val recipientId = DistributionListArchiveImporter.import(item(DistributionId.MY_STORY, name = ""), importState)!!

    val list = SignalDatabase.distributionLists.getList(recipientId)!!
    assertThat(list.name).isEqualTo(DistributionId.MY_STORY.toString())
  }

  @Test
  fun `custom list keeps the name from the archive`() {
    val distributionId = DistributionId.from(UUID.randomUUID())

    val recipientId = DistributionListArchiveImporter.import(item(distributionId, name = "Close friends"), importState)!!

    val list = SignalDatabase.distributionLists.getList(recipientId)!!
    assertThat(list.name).isEqualTo("Close friends")
  }

  private fun item(distributionId: DistributionId, name: String): DistributionListItem {
    return DistributionListItem(
      distributionId = UuidUtil.toByteArray(distributionId.asUuid()).toByteString(),
      distributionList = DistributionList(
        name = name,
        allowReplies = true,
        privacyMode = DistributionList.PrivacyMode.ONLY_WITH
      )
    )
  }
}
