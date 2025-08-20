/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.RegisteredConstraint
import org.thoughtcrime.securesms.migrations.E164FormattingMigrationJob

/**
 * A job that performs the same duties as [E164FormattingMigrationJob], but outside the scope of an app migration.
 * This exists to be run after a backup restore, which might introduce more bad data.
 */
class E164FormattingJob private constructor(params: Parameters) : Job(params) {

  companion object {
    const val KEY = "E164FormattingJob"
  }

  constructor() : this(
    Parameters.Builder()
      .setQueue("E164FormattingJob")
      .setLifespan(Parameters.IMMORTAL)
      .setMaxInstancesForFactory(1)
      .addConstraint(RegisteredConstraint.KEY)
      .build()
  )

  override fun serialize(): ByteArray? = null

  override fun getFactoryKey(): String = KEY

  override fun run(): Result {
    E164FormattingMigrationJob.fixE164Formatting()
    return Result.success()
  }

  override fun onFailure() = Unit

  class Factory : Job.Factory<E164FormattingJob> {
    override fun create(params: Parameters, data: ByteArray?): E164FormattingJob {
      return E164FormattingJob(params)
    }
  }
}
