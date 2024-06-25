/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import org.signal.core.util.getAllTables
import org.signal.core.util.logTime
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.JsonJobData
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

/**
 * Analyzes the database, updating statistics to ensure that sqlite is using the best indices possible for different queries.
 *
 * Given that analysis can be slow, this job will only analyze one table at a time, retrying itself so long as there are more tables to analyze.
 * This should help protect it against getting canceled by the system for running for too long, while also giving it the ability to save it's place.
 */
class AnalyzeDatabaseJob private constructor(
  parameters: Parameters,
  private var lastCompletedTable: String?
) : Job(parameters) {

  companion object {
    val TAG = Log.tag(AnalyzeDatabaseJob::class.java)

    const val KEY = "AnalyzeDatabaseJob"

    private const val KEY_LAST_COMPLETED_TABLE = "last_completed_table"
  }

  constructor() : this(
    Parameters.Builder()
      .setMaxInstancesForFactory(1)
      .setLifespan(1.days.inWholeMilliseconds)
      .setMaxAttempts(Parameters.UNLIMITED)
      .build(),
    null
  )

  override fun serialize(): ByteArray? {
    return JsonJobData.Builder()
      .putString(KEY_LAST_COMPLETED_TABLE, lastCompletedTable)
      .build()
      .serialize()
  }

  override fun getFactoryKey(): String = KEY

  override fun run(): Result {
    val tables = SignalDatabase.rawDatabase.getAllTables()
      .sorted()
      .filterNot { it.startsWith("sqlite_") || it.contains("fts_") }

    if (tables.isEmpty()) {
      Log.w(TAG, "Table list is empty!")
      return Result.success()
    }

    val startingIndex = if (lastCompletedTable != null) {
      tables.indexOf(lastCompletedTable) + 1
    } else {
      0
    }

    if (startingIndex >= tables.size) {
      Log.i(TAG, "Already finished all of the tables!")
      return Result.success()
    }

    val table = tables[startingIndex]

    logTime(TAG, "analyze-$table", decimalPlaces = 2) {
      SignalDatabase.rawDatabase.rawQuery("PRAGMA analysis_limit=1000")
      SignalDatabase.rawDatabase.rawQuery("ANALYZE $table")
    }

    if (startingIndex >= tables.size - 1) {
      Log.i(TAG, "Finished all of the tables!")
      return Result.success()
    }

    lastCompletedTable = table
    return Result.retry(1.seconds.inWholeMilliseconds)
  }

  override fun onFailure() = Unit

  class Factory : Job.Factory<AnalyzeDatabaseJob> {
    override fun create(parameters: Parameters, data: ByteArray?): AnalyzeDatabaseJob {
      val builder = JsonJobData.deserialize(data)

      return AnalyzeDatabaseJob(parameters, builder.getStringOrDefault(KEY_LAST_COMPLETED_TABLE, null))
    }
  }
}
