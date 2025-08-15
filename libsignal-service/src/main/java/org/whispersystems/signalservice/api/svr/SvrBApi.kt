/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.svr

import org.signal.libsignal.attest.AttestationDataException
import org.signal.libsignal.attest.AttestationFailedException
import org.signal.libsignal.messagebackup.BackupKey
import org.signal.libsignal.net.Network
import org.signal.libsignal.net.NetworkException
import org.signal.libsignal.net.NetworkProtocolException
import org.signal.libsignal.net.RetryLaterException
import org.signal.libsignal.net.SvrB
import org.signal.libsignal.net.SvrBRestoreResponse
import org.signal.libsignal.net.SvrBStoreResponse
import org.signal.libsignal.sgxsession.SgxCommunicationFailureException
import org.signal.libsignal.svr.DataMissingException
import org.signal.libsignal.svr.InvalidSvrBDataException
import org.signal.libsignal.svr.RestoreFailedException
import org.whispersystems.signalservice.api.CancelationException
import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.api.backup.MessageBackupKey
import org.whispersystems.signalservice.internal.push.AuthCredentials
import java.io.IOException
import java.util.concurrent.ExecutionException
import kotlin.time.Duration
import kotlin.time.toKotlinDuration

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
        is InvalidSvrBDataException -> StoreResult.InvalidDataError
        is RetryLaterException -> StoreResult.NetworkError(IOException(exception), exception.duration.toKotlinDuration())
        is NetworkException -> StoreResult.NetworkError(exception)
        is NetworkProtocolException -> StoreResult.NetworkError(exception)
        is AttestationFailedException,
        is AttestationDataException,
        is SgxCommunicationFailureException -> StoreResult.SvrError(exception)
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
        is InvalidSvrBDataException -> RestoreResult.InvalidDataError
        is RestoreFailedException -> RestoreResult.RestoreFailedError(exception.triesRemaining)
        is DataMissingException -> RestoreResult.DataMissingError
        is RetryLaterException -> RestoreResult.NetworkError(okio.IOException(exception), exception.duration.toKotlinDuration())
        is NetworkException -> RestoreResult.NetworkError(exception)
        is NetworkProtocolException -> RestoreResult.NetworkError(exception)
        is AttestationFailedException,
        is AttestationDataException,
        is SgxCommunicationFailureException -> RestoreResult.SvrError(exception)
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

  sealed class StoreResult {
    /** Operation succeeded. */
    data class Success(val data: SvrBStoreResponse) : StoreResult()

    /** Indicates the the existing data is unreadable, and you need to start a new chain from the beginning. */
    data object InvalidDataError : StoreResult()

    /** A retryable network error. */
    data class NetworkError(val exception: IOException, val retryAfter: Duration? = null) : StoreResult()

    /** Indicates an issue with the enclave that is outside of our control. We should fail the operation or use a long backoff. */
    data class SvrError(val throwable: Throwable) : StoreResult()

    /** Undocumented error. Crashing recommended. */
    data class UnknownError(val throwable: Throwable) : StoreResult()
  }

  sealed class RestoreResult {
    /** Operation succeeded. */
    data class Success(val data: SvrBRestoreResponse) : RestoreResult()

    /** A retryable network error. */
    data class NetworkError(val exception: IOException, val retryAfter: Duration? = null) : RestoreResult()

    /** No data could be found. This could indicate the user entered their AEP incorrectly. */
    data object DataMissingError : RestoreResult()

    /** Indicate thee existing data is malformed and therefore unrecoverable. */
    data object InvalidDataError : RestoreResult()

    /**
     * Indicates the data was found, but we had the wrong passphrase. While there is a "tries remaining", in practice, this means the data is
     * unrecoverable. We wouldn't get here unless the AEP mapped to a real backup (since that's required to get the backupId), so if we're discovering
     * the passphrase is wrong at this point, it means that an unrecoverable error was made in the past when creating the backup.
     */
    data class RestoreFailedError(val triesRemaining: Int) : RestoreResult()

    /** Indicates an issue with the enclave that is outside of our control. We should fail the operation or use a long backoff. */
    data class SvrError(val throwable: Throwable) : RestoreResult()

    /** Undocumented error. Crashing recommended. */
    data class UnknownError(val throwable: Throwable) : RestoreResult()
  }
}
