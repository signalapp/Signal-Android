/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.thoughtcrime.securesms.jobs

import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobs.protos.Svr3MirrorJobData
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.pin.Svr3Migration
import org.thoughtcrime.securesms.pin.SvrRepository
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException
import org.whispersystems.signalservice.api.svr.SecureValueRecovery.BackupResponse
import org.whispersystems.signalservice.api.svr.SecureValueRecovery.PinChangeSession
import org.whispersystems.signalservice.api.svr.SecureValueRecoveryV3
import kotlin.concurrent.withLock
import kotlin.time.Duration.Companion.days

/**
 * Ensures a user's SVR data is written to SVR3.
 */
class Svr3MirrorJob private constructor(parameters: Parameters, private var serializedChangeSession: String?) : Job(parameters) {

  companion object {
    const val KEY = "Svr3MirrorJob"

    private val TAG = Log.tag(Svr3MirrorJob::class.java)
    private const val KEY_CHANGE_SESSION = "change_session"
  }

  constructor() : this(
    Parameters.Builder()
      .addConstraint(NetworkConstraint.KEY)
      .setLifespan(30.days.inWholeMilliseconds)
      .setMaxAttempts(Parameters.UNLIMITED)
      .setQueue("Svr3MirrorJob")
      .setMaxInstancesForFactory(1)
      .build(),
    null
  )

  override fun serialize(): ByteArray? {
    return Svr3MirrorJobData(
      serializedChangeSession = serializedChangeSession
    ).encode()
  }

  override fun getFactoryKey(): String = KEY

  override fun run(): Result {
    if (!Svr3Migration.shouldWriteToSvr3) {
      Log.w(TAG, "Writes to SVR3 are disabled. Skipping.")
      return Result.success()
    }

    SvrRepository.operationLock.withLock {
      val pin = SignalStore.svr.pin

      if (SignalStore.svr.hasOptedOut()) {
        Log.w(TAG, "Opted out of SVR! Nothing to migrate.")
        return Result.success()
      }

      if (pin == null) {
        Log.w(TAG, "No PIN available! Can't migrate!")
        return Result.success()
      }

      val svr3: SecureValueRecoveryV3 = AppDependencies.signalServiceAccountManager.getSecureValueRecoveryV3(AppDependencies.libsignalNetwork)

      val session: PinChangeSession = serializedChangeSession?.let { session ->
        svr3.resumePinChangeSession(pin, SignalStore.svr.getOrCreateMasterKey(), session)
      } ?: svr3.setPin(pin, SignalStore.svr.getOrCreateMasterKey())

      serializedChangeSession = session.serialize()

      return when (val response: BackupResponse = session.execute()) {
        is BackupResponse.Success -> {
          Log.i(TAG, "Successfully migrated to SVR3! $svr3")
          SignalStore.svr.appendSvr3AuthTokenToList(response.authorization.asBasic())
          AppDependencies.jobManager.add(RefreshAttributesJob())
          Result.success()
        }
        is BackupResponse.ApplicationError -> {
          if (response.exception.isUnauthorized()) {
            Log.w(TAG, "Unauthorized! Giving up.", response.exception)
            Result.success()
          } else {
            Log.w(TAG, "Hit an application error. Retrying.", response.exception)
            Result.retry(defaultBackoff())
          }
        }
        BackupResponse.EnclaveNotFound -> {
          Log.w(TAG, "Could not find the enclave. Giving up.")
          Result.success()
        }
        BackupResponse.ExposeFailure -> {
          Log.w(TAG, "Failed to expose the backup. Giving up.")
          Result.success()
        }
        is BackupResponse.NetworkError -> {
          Log.w(TAG, "Hit a network error. Retrying.", response.exception)
          Result.retry(defaultBackoff())
        }
        BackupResponse.ServerRejected -> {
          Log.w(TAG, "Server told us to stop trying. Giving up.")
          Result.success()
        }
      }
    }
  }

  private fun Throwable.isUnauthorized(): Boolean {
    return this is NonSuccessfulResponseCodeException && this.code == 401
  }

  override fun onFailure() = Unit

  class Factory : Job.Factory<Svr3MirrorJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): Svr3MirrorJob {
      val jobData: Svr3MirrorJobData? = serializedData?.let { Svr3MirrorJobData.ADAPTER.decode(serializedData) }
      return Svr3MirrorJob(parameters, jobData?.serializedChangeSession)
    }
  }
}
