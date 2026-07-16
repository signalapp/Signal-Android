package org.thoughtcrime.securesms.migrations

import kotlinx.coroutines.runBlocking
import org.signal.core.models.ServiceId
import org.signal.core.util.Base64
import org.signal.core.util.logging.Log
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.network.NetworkResult
import org.thoughtcrime.securesms.components.settings.app.changenumber.ChangeNumberRepository
import org.thoughtcrime.securesms.database.model.databaseprotos.PendingChangeNumberMetadata
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobs.AccountConsistencyWorkerJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.net.SignalNetwork
import org.whispersystems.signalservice.internal.push.WhoAmIResponse
import java.io.IOException

/**
 * A server-side bug during change number can commit the number/PNI change but return an error, leaving the client desynced
 * (local still on the old number/PNI). This detects that via whoami and reconciles the local number/PNI. Before adopting
 * the PNI identity from the pending metadata, it verifies that key against the server's published PNI identity, so
 * a stale/overwritten metadata key is never blindly applied.
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
        val serverPni = result.result.pni?.let { ServiceId.PNI.parseOrNull(it) } ?: return

        if (result.result.number == SignalStore.account.e164 && serverPni == SignalStore.account.pni) {
          Log.i(TAG, "No number or PNI mismatch detected.")
          return
        }

        Log.w(TAG, "Detected a number or PNI mismatch! Verifying PNI identity key against the server before fixing...")

        if (pendingPniIdentityMatchesServer(pendingChangeNumberMetadata, serverPni)) {
          Log.w(TAG, "PNI identity key matches server. Fixing local number/PNI...")
          ChangeNumberRepository().changeLocalNumber(result.result.number, serverPni)
          Log.w(TAG, "Done!")
        } else {
          Log.w(TAG, "Server PNI identity does not match pending metadata (or could not be verified); cannot safely reconcile. Enqueuing AccountConsistencyWorkerJob.")
          AppDependencies.jobManager.add(AccountConsistencyWorkerJob())
          return
        }
      }
      is NetworkResult.ApplicationError -> throw result.throwable
      is NetworkResult.NetworkError -> throw result.exception
      is NetworkResult.StatusCodeError -> throw result.exception
    }
  }

  private fun pendingPniIdentityMatchesServer(metadata: PendingChangeNumberMetadata, pni: ServiceId.PNI): Boolean {
    val metadataIdentityKey: IdentityKey = try {
      IdentityKeyPair(metadata.pniIdentityKeyPair.toByteArray()).publicKey
    } catch (e: Exception) {
      Log.w(TAG, "Could not parse PNI identity key from pending metadata.", e)
      return false
    }

    val serverIdentityKey: IdentityKey = when (val profileResult = runBlocking { SignalNetwork.profile.getUnversionedProfile(pni, null) }) {
      is NetworkResult.Success -> {
        val identityKey = profileResult.result.identityKey
        if (identityKey == null) {
          Log.w(TAG, "Server profile for PNI has no identity key; cannot verify.")
          return false
        }
        try {
          IdentityKey(Base64.decode(identityKey), 0)
        } catch (e: Exception) {
          Log.w(TAG, "Could not parse server PNI identity key.", e)
          return false
        }
      }
      is NetworkResult.NetworkError -> throw profileResult.exception
      is NetworkResult.StatusCodeError -> {
        if (profileResult.code == 404) {
          Log.w(TAG, "Could not fetch server profile for PNI (code=${profileResult.code}); cannot verify identity key.")
          return false
        } else {
          throw profileResult.exception
        }
      }
      is NetworkResult.ApplicationError -> throw profileResult.throwable
    }

    return serverIdentityKey.serialize().contentEquals(metadataIdentityKey.serialize())
  }

  override fun shouldRetry(e: Exception): Boolean = e is IOException

  class Factory : Job.Factory<FixChangeNumberErrorMigrationJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): FixChangeNumberErrorMigrationJob {
      return FixChangeNumberErrorMigrationJob(parameters)
    }
  }
}
