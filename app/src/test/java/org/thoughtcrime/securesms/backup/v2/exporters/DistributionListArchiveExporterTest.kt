/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.exporters

import android.app.Application
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.isTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.archive.proto.DistributionList
import org.signal.core.util.UuidUtil
import org.thoughtcrime.securesms.backup.v2.BackupMode
import org.thoughtcrime.securesms.backup.v2.ExportState
import org.thoughtcrime.securesms.backup.v2.database.getAllForBackup
import org.thoughtcrime.securesms.database.DistributionListTables
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.testutil.RecipientTestRule
import org.whispersystems.signalservice.api.push.DistributionId
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class DistributionListArchiveExporterTest {

  @get:Rule
  val recipients = RecipientTestRule()

  @Before
  fun setUp() {
    SignalDatabase.writableDatabase.execSQL("DELETE FROM ${DistributionListTables.ListTable.TABLE_NAME}")
    SignalDatabase.writableDatabase.execSQL("DELETE FROM ${RecipientTable.TABLE_NAME} WHERE ${RecipientTable.TYPE} = ${RecipientTable.RecipientType.DISTRIBUTION_LIST.id}")
  }

  @Test
  fun `My Story is exported with an empty name`() {
    SignalDatabase.distributionLists.createList(name = DistributionId.MY_STORY.toString(), members = emptyList(), distributionId = DistributionId.MY_STORY)

    val exported = export().single { UuidUtil.fromByteString(it.distributionId) == DistributionId.MY_STORY.asUuid() }

    assertThat(exported.distributionList!!.name).isEqualTo("")
  }

  @Test
  fun `My Story marked as deleted is exported as a live list`() {
    SignalDatabase.distributionLists.createList(name = DistributionId.MY_STORY.toString(), members = emptyList(), distributionId = DistributionId.MY_STORY, deletionTimestamp = 1000L)

    val exported = export().single { UuidUtil.fromByteString(it.distributionId) == DistributionId.MY_STORY.asUuid() }

    assertThat(exported.deletionTimestamp).isNull()
    assertThat(exported.distributionList!!.name).isEqualTo("")
    assertThat(exported.distributionList!!.allowReplies).isTrue()
    assertThat(exported.distributionList!!.privacyMode).isEqualTo(DistributionList.PrivacyMode.ALL)
    assertThat(exported.distributionList!!.memberRecipientIds).isEmpty()
  }

  @Test
  fun `deleted custom list is exported as deleted`() {
    val distributionId = DistributionId.from(UUID.randomUUID())
    SignalDatabase.distributionLists.createList(name = "Close friends", members = emptyList(), distributionId = distributionId, deletionTimestamp = 1000L)

    val exported = export().single { UuidUtil.fromByteString(it.distributionId) == distributionId.asUuid() }

    assertThat(exported.deletionTimestamp).isEqualTo(1000L)
    assertThat(exported.distributionList).isNull()
  }

  @Test
  fun `custom list is exported with its name`() {
    val distributionId = DistributionId.from(UUID.randomUUID())
    SignalDatabase.distributionLists.createList(name = "Close friends", members = emptyList(), distributionId = distributionId)

    val exported = export().single { UuidUtil.fromByteString(it.distributionId) == distributionId.asUuid() }

    assertThat(exported.distributionList!!.name).isEqualTo("Close friends")
  }

  private fun export() = SignalDatabase.distributionLists
    .getAllForBackup(recipients.self, ExportState(backupTime = 0L, backupMode = BackupMode.REMOTE, selfRecipientId = recipients.self))
    .use { exporter -> exporter.asSequence().map { it.distributionList!! }.toList() }
}
