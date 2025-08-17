/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.svr

import org.signal.libsignal.attest.AttestationFailedException
import org.signal.libsignal.internal.CompletableFuture
import org.signal.libsignal.messagebackup.BackupKey
import org.signal.libsignal.net.Network
import org.signal.libsignal.net.NetworkException
import org.signal.libsignal.net.NetworkProtocolException
import org.signal.libsignal.net.SvrB
import org.signal.libsignal.net.SvrBRestoreResponse
import org.signal.libsignal.net.SvrBStoreResponse
import org.signal.libsignal.svr.DataMissingException
import org.signal.libsignal.svr.RestoreFailedException
import org.signal.libsignal.svr.SvrException
import org.whispersystems.signalservice.api.CancelationException
import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.api.backup.MessageBackupKey
import org.whispersystems.signalservice.internal.push.AuthCredentials
import java.io.IOException
import java.util.concurrent.ExecutionException

/**
 * A collection of operations for interacting with SVRB, the SVR enclave that provides forward secrecy for backups.
 */
class SvrBApi(private val network: Network) {

  /**
   * See [SvrB.createNewBackupChain].
   *
   * Call this the first time you ever interact with SVRB. Gives you a secret data to persist and use for future calls.
   *
   * Note that this doesn't actually make a network call, it just needs a [Network] to get the environment.
   */
  fun createNewBackupChain(auth: AuthCredentials, backupKey: MessageBackupKey): ByteArray {
    return network
      .svrB(auth.username(), auth.password())
      .createNewBackupChain(BackupKey(backupKey.value))
  }

  /**
   * See [SvrB.store].
   *
   * Handling this one is funny because the underlying protocols don't use status codes, instead favoring complex results.
   * As a result, responses are only [NetworkResult.Success] and [NetworkResult.NetworkError], with errors being accounted for
   * in the success case via the sealed result class.
   */
  fun store(auth: AuthCredentials, backupKey: MessageBackupKey, previousSecretData: ByteArray): StoreResult {
    return try {
      val result = network
        .svrB(auth.username(), auth.password())
        .store(BackupKey(backupKey.value), previousSecretData)
        .get()

      when (val exception = result.exceptionOrNull()) {
        null -> StoreResult.Success(result.getOrThrow())
        is NetworkException -> StoreResult.NetworkError(exception)
        is NetworkProtocolException -> StoreResult.NetworkError(exception)
        is SvrException -> StoreResult.SvrError(exception)
        else -> StoreResult.UnknownError(exception)
      }
    } catch (e: CancelationException) {
      StoreResult.UnknownError(e)
    } catch (e: ExecutionException) {
      StoreResult.UnknownError(e)
    } catch (e: InterruptedException) {
      StoreResult.UnknownError(e)
    }
  }

  /**
   * See [SvrB.restore]
   *
   * Handling this one is funny because the underlying protocols don't use status codes, instead favoring complex results.
   * As a result, responses are only [NetworkResult.Success] and [NetworkResult.NetworkError], with errors being accounted for
   * in the success case via the sealed result class.
   */
  fun restore(auth: AuthCredentials, backupKey: MessageBackupKey, forwardSecrecyMetadata: ByteArray): RestoreResult {
    return try {
      val result = network
        .svrB(auth.username(), auth.password())
        .restore(BackupKey(backupKey.value), forwardSecrecyMetadata)
        .get()

      when (val exception = result.exceptionOrNull()) {
        null -> RestoreResult.Success(result.getOrThrow())
        is NetworkException -> RestoreResult.NetworkError(exception)
        is NetworkProtocolException -> RestoreResult.NetworkError(exception)
        is DataMissingException -> RestoreResult.DataMissingError
        is RestoreFailedException -> RestoreResult.RestoreFailedError(exception.triesRemaining)
        is SvrException -> RestoreResult.SvrError(exception)
        is AttestationFailedException -> RestoreResult.SvrError(exception)
        else -> RestoreResult.UnknownError(exception)
      }
    } catch (e: CancelationException) {
      RestoreResult.UnknownError(e)
    } catch (e: ExecutionException) {
      RestoreResult.UnknownError(e)
    } catch (e: InterruptedException) {
      RestoreResult.UnknownError(e)
    }
  }

  private fun <ResultType, FinalType> CompletableFuture<Result<ResultType>>.toNetworkResult(resultMapper: (Result<ResultType>) -> FinalType): NetworkResult<FinalType> {
    return try {
      val result = this.get()
      return when (val exception = result.exceptionOrNull()) {
        is NetworkException -> NetworkResult.NetworkError(exception)
        is NetworkProtocolException -> NetworkResult.NetworkError(exception)
        else -> NetworkResult.Success(resultMapper(result))
      }
    } catch (e: CancelationException) {
      NetworkResult.Success(resultMapper(Result.failure(e)))
    } catch (e: ExecutionException) {
      NetworkResult.Success(resultMapper(Result.failure(e)))
    } catch (e: InterruptedException) {
      NetworkResult.Success(resultMapper(Result.failure(e)))
    }
  }

  sealed class StoreResult {
    data class Success(val data: SvrBStoreResponse) : StoreResult()
    data class NetworkError(val exception: IOException) : StoreResult()
    data class SvrError(val throwable: Throwable) : StoreResult()
    data class UnknownError(val throwable: Throwable) : StoreResult()
  }

  sealed class RestoreResult {
    data class Success(val data: SvrBRestoreResponse) : RestoreResult()
    data class NetworkError(val exception: IOException) : RestoreResult()
    data object DataMissingError : RestoreResult()
    data class RestoreFailedError(val triesRemaining: Int) : RestoreResult()
    data class SvrError(val throwable: Throwable) : RestoreResult()
    data class UnknownError(val throwable: Throwable) : RestoreResult()
  }
}
