/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.util.insertInto
import org.signal.core.util.readToSingleInt
import org.signal.core.util.select
import org.thoughtcrime.securesms.testutil.SignalDatabaseMigrationRule

@Suppress("ClassName")
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class V330_AddSystemContactLinkStateToRecipientTableTest {

  @get:Rule val signalDatabaseRule = SignalDatabaseMigrationRule(329)

  private val db get() = signalDatabaseRule.database

  @Test
  fun migrate_linkedRecipientIsLinked() {
    insertRecipient(e164 = "+15555550101", contactUri = "content://com.android.contacts/contacts/lookup/0r1-ABC/1")

    migrate()

    assertThat(linkStateOf("+15555550101")).isEqualTo(1)
  }

  @Test
  fun migrate_unlinkedRecipientIsNotLinked() {
    insertRecipient(e164 = "+15555550101", contactUri = null)

    migrate()

    assertThat(linkStateOf("+15555550101")).isEqualTo(0)
  }

  private fun migrate() {
    V330_AddSystemContactLinkStateToRecipientTable.migrate(ApplicationProvider.getApplicationContext(), db, 329, 330)
  }

  private fun insertRecipient(e164: String, contactUri: String?) {
    db.insertInto("recipient")
      .values(
        "e164" to e164,
        "system_contact_uri" to contactUri
      )
      .run()
  }

  private fun linkStateOf(e164: String): Int {
    return db.select("system_contact_link_state").from("recipient").where("e164 = ?", e164).run().readToSingleInt()
  }
}
