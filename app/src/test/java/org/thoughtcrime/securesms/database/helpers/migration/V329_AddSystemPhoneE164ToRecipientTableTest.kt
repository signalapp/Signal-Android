/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.util.insertInto
import org.signal.core.util.readToSingleObject
import org.signal.core.util.requireString
import org.signal.core.util.select
import org.thoughtcrime.securesms.testutil.SignalDatabaseMigrationRule

@Suppress("ClassName")
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class V329_AddSystemPhoneE164ToRecipientTableTest {

  @get:Rule val signalDatabaseRule = SignalDatabaseMigrationRule(328)

  private val db get() = signalDatabaseRule.database

  @Test
  fun migrate_linkedRecipientGetsItsNumber() {
    insertRecipient(e164 = "+15555550101", contactUri = "content://com.android.contacts/contacts/lookup/0r1-ABC/1")

    migrate()

    assertThat(systemPhoneE164Of("+15555550101")).isEqualTo("+15555550101")
  }

  @Test
  fun migrate_unlinkedRecipientGetsNothing() {
    insertRecipient(e164 = "+15555550101", contactUri = null)

    migrate()

    assertThat(systemPhoneE164Of("+15555550101")).isNull()
  }

  private fun migrate() {
    V329_AddSystemPhoneE164ToRecipientTable.migrate(ApplicationProvider.getApplicationContext(), db, 328, 329)
  }

  private fun insertRecipient(e164: String, contactUri: String?) {
    db.insertInto("recipient")
      .values(
        "e164" to e164,
        "system_contact_uri" to contactUri
      )
      .run()
  }

  private fun systemPhoneE164Of(e164: String): String? {
    return db.select("system_phone_e164").from("recipient").where("e164 = ?", e164).run().readToSingleObject { it.requireString("system_phone_e164") }
  }
}
