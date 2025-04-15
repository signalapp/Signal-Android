package org.thoughtcrime.securesms.migrations

import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.components.settings.app.changenumber.ChangeNumberRepository
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.net.SignalNetwork
import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.api.push.ServiceId
import org.whispersystems.signalservice.internal.push.WhoAmIResponse
import java.io.IOException

/**
 * There was a server error during change number where a number was changed but gave back a 409 response.
 * We need devices to re-fetch their E164+PNI's, save them, and then get prekeys.
 */
internal class FixChangeNumberErrorMigrationJob(
  parameters: Parameters = Parameters.Builder().build()
) : MigrationJob(parameters) {
  companion object {
    const val KEY = "FixChangeNumberErrorMigrationJob"
    private val TAG = Log.tag(FixChangeNumberErrorMigrationJob::class)
  }

  override fun getFactoryKey(): String = KEY

  override fun isUiBlocking(): Boolean = false

  override fun performMigration() {
    if (!SignalStore.account.isRegistered) {
      Log.i(TAG, "Not registered, skipping.")
      return
    }

    val pendingChangeNumberMetadata = SignalStore.misc.pendingChangeNumberMetadata

    if (pendingChangeNumberMetadata == null) {
      Log.i(TAG, "No pending change number metadata, skipping.")
      return
    }

    if (pendingChangeNumberMetadata.previousPni != SignalStore.account.pni?.toByteString()) {
      Log.i(TAG, "Pending change number operation isn't for our current PNI, skipping.")
      return
    }

    when (val result = SignalNetwork.account.whoAmI()) {
      is NetworkResult.Success<WhoAmIResponse> -> {
        val pni = result.result.pni?.let { ServiceId.PNI.parseOrNull(it) } ?: return

        if (result.result.number != SignalStore.account.e164 || pni != SignalStore.account.pni) {
          Log.w(TAG, "Detected a number or PNI mismatch! Fixing...")
          ChangeNumberRepository().changeLocalNumber(result.result.number, pni)
          Log.w(TAG, "Done!")
        } else {
          Log.i(TAG, "No number or PNI mismatch detected.")
          return
        }
      }
      is NetworkResult.ApplicationError -> throw result.throwable
      is NetworkResult.NetworkError -> throw result.exception
      is NetworkResult.StatusCodeError -> throw result.exception
    }
  }

  override fun shouldRetry(e: Exception): Boolean = e is IOException

  class Factory : Job.Factory<FixChangeNumberErrorMigrationJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): FixChangeNumberErrorMigrationJob {
      return FixChangeNumberErrorMigrationJob(parameters)
    }
  }
}
