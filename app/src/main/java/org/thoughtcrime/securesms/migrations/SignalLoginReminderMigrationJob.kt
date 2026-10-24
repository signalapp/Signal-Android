/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.migrations

import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.megaphone.SignalLoginReminderSchedule

/**
 * Starts the Signal Login reminder clock for phone-numberless accounts that registered before the app started tracking
 * it, so their first reminder comes two weeks after this update.
 */
internal class SignalLoginReminderMigrationJob private constructor(parameters: Parameters) : MigrationJob(parameters) {

  companion object {
    const val KEY = "SignalLoginReminderMigrationJob"

    private val TAG = Log.tag(SignalLoginReminderMigrationJob::class)
  }

  internal constructor() : this(Parameters.Builder().build())

  override fun isUiBlocking(): Boolean = false

  override fun getFactoryKey(): String = KEY

  override fun performMigration() {
    if (!SignalStore.account.isRegistered || SignalStore.account.aci == null) {
      Log.i(TAG, "Unregistered, skipping.")
      return
    }

    if (!SignalStore.account.isPhoneNumberless) {
      Log.i(TAG, "Not a phone-numberless account, skipping.")
      return
    }

    SignalLoginReminderSchedule.startReminderClockIfNeeded()
  }

  override fun shouldRetry(e: Exception): Boolean = false

  class Factory : Job.Factory<SignalLoginReminderMigrationJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): SignalLoginReminderMigrationJob {
      return SignalLoginReminderMigrationJob(parameters)
    }
  }
}
