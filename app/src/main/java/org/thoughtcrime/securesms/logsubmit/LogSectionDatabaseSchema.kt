package org.thoughtcrime.securesms.logsubmit

import android.content.Context
import org.signal.core.util.getAllIndexDefinitions
import org.signal.core.util.getAllTableDefinitions
import org.signal.core.util.getForeignKeys
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.helpers.SignalDatabaseMigrations

/**
 * Renders data pertaining to sender key. While all private info is obfuscated, this is still only intended to be printed for internal users.
 */
class LogSectionDatabaseSchema : LogSection {
  override fun getTitle(): String {
    return "DATABASE SCHEMA"
  }

  override fun getContent(context: Context): CharSequence {
    val builder = StringBuilder()
    builder.append("--- Metadata").append("\n")
    builder.append("Version: ${SignalDatabaseMigrations.DATABASE_VERSION}\n")
    builder.append("\n\n")

    builder.append("--- Tables").append("\n")
    SignalDatabase.rawDatabase.getAllTableDefinitions().forEach {
      builder.append(it.statement).append("\n")
    }
    builder.append("\n\n")

    builder.append("--- Indexes").append("\n")
    SignalDatabase.rawDatabase.getAllIndexDefinitions().forEach {
      builder.append(it.statement).append("\n")
    }
    builder.append("\n\n")

    builder.append("--- Foreign Keys").append("\n")
    SignalDatabase.rawDatabase.getForeignKeys().forEach {
      builder.append("${it.table}.${it.column} DEPENDS ON ${it.dependsOnTable}.${it.dependsOnColumn}, ON DELETE ${it.onDelete}").append("\n")
    }
    builder.append("\n\n")

    return builder
  }
}
