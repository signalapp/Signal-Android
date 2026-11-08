/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.migrations

import kotlinx.coroutines.runBlocking
import org.signal.core.util.logging.Log
import org.signal.libsignal.net.RequestResult
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.megaphone.TwoFactorReminderSchedule
import org.thoughtcrime.securesms.net.SignalNetwork
import java.io.IOException

/**
 * Asks the service whether the account already has two-factor methods, since ones added before the app started
 * tracking them would otherwise never trigger the reminder megaphone.
 */
internal class TwoFactorMethodsMigrationJob private constructor(parameters: Parameters) : MigrationJob(parameters) {

  companion object {
    const val KEY = "TwoFactorMethodsMigrationJob"

    private val TAG = Log.tag(TwoFactorMethodsMigrationJob::class)
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

    when (val result = runBlocking { SignalNetwork.accountApiV2.listMfaKeys(SignalStore.svr.masterKey) }) {
      is RequestResult.Success -> {
        Log.i(TAG, "Account has ${result.result.size} two-factor method(s).")
        TwoFactorReminderSchedule.onMethodsObserved(result.result.isNotEmpty())
      }
      is RequestResult.NonSuccess -> {
        error("Code branch is unreachable")
      }
      is RequestResult.RetryableNetworkError -> {
        throw result.networkError
      }
      is RequestResult.ApplicationError -> {
        Log.w(TAG, "Couldn't list two-factor methods, skipping.", result.cause)
      }
    }
  }

  override fun shouldRetry(e: Exception): Boolean = e is IOException

  class Factory : Job.Factory<TwoFactorMethodsMigrationJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): TwoFactorMethodsMigrationJob {
      return TwoFactorMethodsMigrationJob(parameters)
    }
  }
}
