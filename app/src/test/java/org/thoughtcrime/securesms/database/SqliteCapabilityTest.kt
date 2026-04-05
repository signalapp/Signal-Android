package org.thoughtcrime.securesms.database

import android.app.Application
import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.util.readToList
import org.signal.core.util.requireString
import org.thoughtcrime.securesms.testutil.MockAppDependenciesRule
import org.thoughtcrime.securesms.testutil.SignalDatabaseRule

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class SqliteCapabilityTest {

  @get:Rule
  val appDependencies = MockAppDependenciesRule()

  @get:Rule
  val signalDatabaseRule = SignalDatabaseRule()

  @Test
  fun `json_each is supported`() {
    val values = signalDatabaseRule.readableDatabase
      .rawQuery("SELECT value FROM json_each('[1,2,3]')", null)
      .readToList { cursor -> cursor.requireString("value") }

    assertThat(values).isEqualTo(listOf("1", "2", "3"))
  }
}
