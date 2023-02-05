package org.thoughtcrime.securesms.database.helpers.migration

import android.app.Application
import androidx.core.content.contentValuesOf
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.signal.core.util.SqlUtil
import org.thoughtcrime.securesms.database.DistributionListTables
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.DistributionListId
import org.thoughtcrime.securesms.testing.SignalDatabaseRule
import org.whispersystems.signalservice.api.push.DistributionId
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class MyStoryMigrationTest {

  @get:Rule val harness = SignalDatabaseRule(deleteAllThreadsOnEachRun = false)

  @Test
  fun givenAValidMyStory_whenIMigrate_thenIExpectMyStoryToBeValid() {
    // GIVEN
    assertValidMyStoryExists()

    // WHEN
    runMigration()

    // THEN
    assertValidMyStoryExists()
  }

  @Test
  fun givenNoMyStory_whenIMigrate_thenIExpectMyStoryToBeCreated() {
    // GIVEN
    deleteMyStory()

    // WHEN
    runMigration()

    // THEN
    assertValidMyStoryExists()
  }

  @Test
  fun givenA00000000DistributionIdForMyStory_whenIMigrate_thenIExpectMyStoryToBeCreated() {
    // GIVEN
    setMyStoryDistributionId("0000-0000")

    // WHEN
    runMigration()

    // THEN
    assertValidMyStoryExists()
  }

  @Test
  fun givenARandomDistributionIdForMyStory_whenIMigrate_thenIExpectMyStoryToBeCreated() {
    // GIVEN
    setMyStoryDistributionId(UUID.randomUUID().toString())

    // WHEN
    runMigration()

    // THEN
    assertValidMyStoryExists()
  }

  private fun setMyStoryDistributionId(serializedId: String) {
    SignalDatabase.rawDatabase.update(
      DistributionListTables.LIST_TABLE_NAME,
      contentValuesOf(
        DistributionListTables.DISTRIBUTION_ID to serializedId
      ),
      "_id = ?",
      SqlUtil.buildArgs(DistributionListId.MY_STORY)
    )
  }

  private fun deleteMyStory() {
    SignalDatabase.rawDatabase.delete(
      DistributionListTables.LIST_TABLE_NAME,
      "_id = ?",
      SqlUtil.buildArgs(DistributionListId.MY_STORY)
    )
  }

  private fun assertValidMyStoryExists() {
    SignalDatabase.rawDatabase.query(
      DistributionListTables.LIST_TABLE_NAME,
      SqlUtil.COUNT,
      "_id = ? AND ${DistributionListTables.DISTRIBUTION_ID} = ?",
      SqlUtil.buildArgs(DistributionListId.MY_STORY, DistributionId.MY_STORY.toString()),
      null,
      null,
      null
    ).use {
      if (it.moveToNext()) {
        val count = it.getInt(0)
        assertEquals("assertValidMyStoryExists: Query produced an unexpected count.", 1, count)
      } else {
        fail("assertValidMyStoryExists: Query did not produce a count.")
      }
    }
  }

  private fun runMigration() {
    V151_MyStoryMigration.migrate(
      InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application,
      SignalDatabase.rawDatabase,
      0,
      1
    )
  }
}
