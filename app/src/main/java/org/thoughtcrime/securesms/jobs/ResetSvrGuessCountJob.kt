/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.thoughtcrime.securesms.jobs

import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.BuildConfig
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.JsonJobData
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.pin.SvrRepository
import org.whispersystems.signalservice.api.kbs.MasterKey
import org.whispersystems.signalservice.api.svr.SecureValueRecovery
import org.whispersystems.signalservice.api.svr.SecureValueRecovery.BackupResponse
import org.whispersystems.signalservice.api.svr.SecureValueRecovery.PinChangeSession
import kotlin.concurrent.withLock
import kotlin.time.Duration.Companion.days

/**
 * Attempts to reset the guess on the SVR PIN. Intended to be enqueued after a successful restore.
 */
class ResetSvrGuessCountJob private constructor(
  parameters: Parameters,
  private val serializedChangeSession: String?,
  private var svr2Complete: Boolean
) : Job(parameters) {

  companion object {
    const val KEY = "ResetSvrGuessCountJob"

    private val TAG = Log.tag(ResetSvrGuessCountJob::class.java)

    private const val KEY_CHANGE_SESSION = "change_session"
    private const val KEY_SVR2_COMPLETE = "svr2_complete"
  }

  constructor() : this(
    Parameters.Builder()
      .addConstraint(NetworkConstraint.KEY)
      .setLifespan(1.days.inWholeMilliseconds)
      .setMaxAttempts(Parameters.UNLIMITED)
      .setQueue("ResetSvrGuessCountJob")
      .setMaxInstancesForFactory(1)
      .build(),
    null,
    false
  )

  override fun serialize(): ByteArray? {
    return JsonJobData.Builder()
      .putString(KEY_CHANGE_SESSION, serializedChangeSession)
      .putBoolean(KEY_SVR2_COMPLETE, svr2Complete)
      .build()
      .serialize()
  }

  override fun getFactoryKey(): String = KEY

  override fun run(): Result {
    SvrRepository.operationLock.withLock {
      val pin = SignalStore.svr().pin

      if (SignalStore.svr().hasOptedOut()) {
        Log.w(TAG, "Opted out of SVR! Nothing to migrate.")
        return Result.success()
      }

      if (pin == null) {
        Log.w(TAG, "No PIN available! Can't migrate!")
        return Result.success()
      }

      val masterKey: MasterKey = SignalStore.svr().getOrCreateMasterKey()

      val svr2Result = if (!svr2Complete) {
        resetGuessCount(AppDependencies.signalServiceAccountManager.getSecureValueRecoveryV2(BuildConfig.SVR2_MRENCLAVE), pin, masterKey)
      } else {
        Log.d(TAG, "Already reset guess count on SVR2. Skipping.")
        Result.success()
      }

      return svr2Result
    }
  }

  override fun onFailure() = Unit

  private fun resetGuessCount(svr: SecureValueRecovery, pin: String, masterKey: MasterKey): Result {
    val session: PinChangeSession = if (serializedChangeSession != null) {
      svr.resumePinChangeSession(pin, SignalStore.svr().getOrCreateMasterKey(), serializedChangeSession)
    } else {
      svr.setPin(pin, masterKey)
    }

    return when (val response: BackupResponse = session.execute()) {
      is BackupResponse.Success -> {
        Log.i(TAG, "Successfully reset guess count. $svr")
        SignalStore.svr().appendAuthTokenToList(response.authorization.asBasic())
        Result.success()
      }
      is BackupResponse.ApplicationError -> {
        Log.w(TAG, "Hit an application error. Retrying. $svr", response.exception)
        Result.retry(defaultBackoff())
      }
      BackupResponse.EnclaveNotFound -> {
        Log.w(TAG, "Could not find the enclave. Giving up. $svr")
        Result.success()
      }
      BackupResponse.ExposeFailure -> {
        Log.w(TAG, "Failed to expose the backup. Giving up. $svr")
        Result.success()
      }
      is BackupResponse.NetworkError -> {
        Log.w(TAG, "Hit a network error. Retrying. $svr", response.exception)
        Result.retry(defaultBackoff())
      }
      BackupResponse.ServerRejected -> {
        Log.w(TAG, "Server told us to stop trying. Giving up. $svr")
        Result.success()
      }
    }
  }

  class Factory : Job.Factory<ResetSvrGuessCountJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): ResetSvrGuessCountJob {
      val data = JsonJobData.deserialize(serializedData)

      return ResetSvrGuessCountJob(
        parameters,
        data.getString(KEY_CHANGE_SESSION),
        data.getBoolean(KEY_SVR2_COMPLETE)
      )
    }
  }
}
