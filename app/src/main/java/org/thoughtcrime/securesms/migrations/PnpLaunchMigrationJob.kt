/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.migrations

import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobs.ProfileUploadJob
import org.thoughtcrime.securesms.jobs.RefreshAttributesJob
import java.lang.Exception

/**
 * Kicks off a chain of jobs to update the server with our latest PNP settings.
 */
internal class PnpLaunchMigrationJob(parameters: Parameters = Parameters.Builder().build()) : MigrationJob(parameters) {
  companion object {
    const val KEY = "PnpLaunchMigrationJob"
  }

  override fun getFactoryKey(): String = KEY

  override fun isUiBlocking(): Boolean = false

  override fun performMigration() {
    AppDependencies.jobManager
      .startChain(RefreshAttributesJob())
      .then(ProfileUploadJob())
      .enqueue()
  }

  override fun shouldRetry(e: Exception): Boolean = false

  class Factory : Job.Factory<PnpLaunchMigrationJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): PnpLaunchMigrationJob {
      return PnpLaunchMigrationJob(parameters)
    }
  }
}
