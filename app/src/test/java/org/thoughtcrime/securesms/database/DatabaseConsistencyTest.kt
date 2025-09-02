/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database

import android.app.Application
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.util.ForeignKeyConstraint
import org.signal.core.util.Index
import org.signal.core.util.getForeignKeys
import org.signal.core.util.getIndexes
import org.signal.core.util.readToList
import org.signal.core.util.requireNonNullString
import org.thoughtcrime.securesms.database.helpers.SignalDatabaseMigrations
import org.thoughtcrime.securesms.testutil.SignalDatabaseMigrationRule
import org.thoughtcrime.securesms.testutil.SignalDatabaseRule

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class DatabaseConsistencyTest {

  @get:Rule
  val signalDatabaseRule = SignalDatabaseRule()

  @get:Rule
  val signalDatabaseMigrationRule = SignalDatabaseMigrationRule(SignalDatabaseMigrations.DATABASE_VERSION)

  @Test
  fun testUpgradeConsistency() {
    val currentVersionStatements = signalDatabaseRule.readableDatabase.getAllCreateStatements()
    val upgradedStatements = signalDatabaseMigrationRule.database.getAllCreateStatements()

    if (currentVersionStatements != upgradedStatements) {
      var message = "\n"

      val currentByName = currentVersionStatements.associateBy { it.name }
      val upgradedByName = upgradedStatements.associateBy { it.name }

      if (currentByName.keys != upgradedByName.keys) {
        val exclusiveToCurrent = currentByName.keys - upgradedByName.keys
        val exclusiveToUpgrade = upgradedByName.keys - currentByName.keys

        message += "SQL entities exclusive to the newly-created database: $exclusiveToCurrent\n"
        message += "SQL entities exclusive to the upgraded database: $exclusiveToUpgrade\n\n"
      } else {
        for (currentEntry in currentByName) {
          val upgradedValue: SignalDatabaseMigrationRule.Statement = upgradedByName[currentEntry.key]!!
          if (upgradedValue.sql != currentEntry.value.sql) {
            message += "Statement differed:\n"
            message += "newly-created:\n"
            message += "${currentEntry.value.sql}\n\n"
            message += "upgraded:\n"
            message += "${upgradedValue.sql}\n\n"
          }
        }
      }

      Assert.assertTrue(message, false)
    }
  }

  @Test
  fun testForeignKeyIndexCoverage() {
    /** We may deem certain indexes non-critical if deletion frequency is low or table size is small. */
    val ignoredColumns: List<Pair<String, String>> = listOf(
      StorySendTable.TABLE_NAME to StorySendTable.DISTRIBUTION_ID
    )

    val foreignKeys: List<ForeignKeyConstraint> = signalDatabaseRule.writeableDatabase.getForeignKeys()
    val indexesByFirstColumn: List<Index> = signalDatabaseRule.writeableDatabase.getIndexes()

    val notFound: List<Pair<String, String>> = foreignKeys
      .filterNot { ignoredColumns.contains(it.table to it.column) }
      .filterNot { foreignKey ->
        indexesByFirstColumn.hasPrimaryIndexFor(foreignKey.table, foreignKey.column)
      }
      .map { it.table to it.column }

    Assert.assertTrue("Missing indexes to cover: $notFound", notFound.isEmpty())
  }

  private fun List<Index>.hasPrimaryIndexFor(table: String, column: String): Boolean {
    return this.any { index -> index.table == table && index.columns[0] == column }
  }

  private fun SQLiteDatabase.getAllCreateStatements(): List<SignalDatabaseMigrationRule.Statement> {
    return this
      .rawQuery("SELECT name, sql FROM sqlite_master WHERE sql NOT NULL AND name != 'sqlite_sequence' AND name != 'android_metadata'")
      .readToList { cursor ->
        SignalDatabaseMigrationRule.Statement(
          name = cursor.requireNonNullString("name"),
          sql = cursor.requireNonNullString("sql").normalizeSql()
        )
      }
      .filterNot { it.name.startsWith("sqlite_stat") }
      .sortedBy { it.name }
  }

  @Suppress("SimplifiableCallChain")
  private fun String.normalizeSql(): String {
    return this
      .split("\n")
      .map { it.trim() }
      .joinToString(separator = " ")
      .replace(Regex.fromLiteral(" ,"), ",")
      .replace(",([^\\s])".toRegex(), ", $1")
      .replace(Regex("\\s+"), " ")
      .replace(Regex.fromLiteral("( "), "(")
      .replace(Regex.fromLiteral(" )"), ")")
      .replace(Regex("CREATE TABLE \"([a-zA-Z_]+)\""), "CREATE TABLE $1") // for some reason SQLite will wrap table names in quotes for upgraded tables. This unwraps them.
  }
}
