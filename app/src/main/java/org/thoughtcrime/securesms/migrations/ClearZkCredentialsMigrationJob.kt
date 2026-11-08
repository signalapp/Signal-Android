/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.migrations

import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job

/**
 * Clears cached group and call link auth credentials, which were issued under the previous
 * GENERIC_SERVER_PUBLIC_PARAMS and can no longer be verified.
 */
internal class ClearZkCredentialsMigrationJob private constructor(parameters: Parameters) : MigrationJob(parameters) {

  companion object {
    const val KEY = "ClearZkCredentialsMigrationJob"

    private val TAG = Log.tag(ClearZkCredentialsMigrationJob::class)
  }

  internal constructor() : this(Parameters.Builder().build())

  override fun isUiBlocking(): Boolean = false

  override fun getFactoryKey(): String = KEY

  override fun performMigration() {
    Log.i(TAG, "Clearing cached group and call link auth credentials")
    // Under the hood this clears both group + calls
    AppDependencies.groupsV2Authorization.clear()
  }

  override fun shouldRetry(e: Exception): Boolean = false

  class Factory : Job.Factory<ClearZkCredentialsMigrationJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): ClearZkCredentialsMigrationJob {
      return ClearZkCredentialsMigrationJob(parameters)
    }
  }
}
