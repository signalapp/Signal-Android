/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.exporters

import android.app.Application
import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
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
