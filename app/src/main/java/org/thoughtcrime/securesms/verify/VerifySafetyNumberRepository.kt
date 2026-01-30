package org.thoughtcrime.securesms.verify

import org.signal.core.util.logging.Log
import org.signal.libsignal.net.KeyTransparency
import org.signal.libsignal.net.RequestResult
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil
import org.thoughtcrime.securesms.database.model.KeyTransparencyStore
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.net.SignalNetwork
import org.thoughtcrime.securesms.recipients.Recipient
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess
import java.time.Duration

/**
 * Repository for safety numbers, namely to support key transparency / automatic verification
 */
object VerifySafetyNumberRepository {

  private val TAG = Log.tag(VerifySafetyNumberRepository::class.java)

  /**
   * Given a recipient will try to verify via search (first time) or monitor (subsequent).
   */
  suspend fun verifyAutomatically(recipient: Recipient): VerifyResult {
    if (recipient.aci.isEmpty || recipient.e164.isEmpty) {
      return VerifyResult.UnretryableFailure
    }

    val identityRecord = AppDependencies.protocolStore.aci().identities().getIdentityRecord(recipient.id)
    val aciIdentityKey = identityRecord.get().identityKey
    val aci = recipient.requireAci().libSignalAci
    val e164 = recipient.requireE164()
    val unidentifiedAccessKey = ProfileKeyUtil.profileKeyOrNull(recipient.profileKey).let { UnidentifiedAccess.deriveAccessKeyFrom(it) }
    val monitorMode = if (recipient.isSelf) KeyTransparency.MonitorMode.SELF else KeyTransparency.MonitorMode.OTHER
    val firstSearch = recipient.keyTransparencyData == null

    val result = if (firstSearch) {
      Log.i(TAG, "First search in key transparency")
      SignalNetwork.keyTransparency.search(aci, aciIdentityKey, e164, unidentifiedAccessKey, KeyTransparencyStore)
    } else {
      Log.i(TAG, "Monitoring search in key transparency")
      SignalNetwork.keyTransparency.monitor(monitorMode, aci, aciIdentityKey, e164, unidentifiedAccessKey, KeyTransparencyStore)
    }

    Log.i(TAG, "Key transparency complete, result: $result")
    return when (result) {
      is RequestResult.Success -> {
        VerifyResult.Success
      }
      is RequestResult.NonSuccess -> {
        if (result.error.exception is IllegalArgumentException) {
          VerifyResult.CorruptedFailure
        } else {
          VerifyResult.UnretryableFailure
        }
      }
      is RequestResult.RetryableNetworkError -> {
        if (result.retryAfter != null) {
          VerifyResult.RetryableFailure(result.retryAfter!!)
        } else {
          VerifyResult.UnretryableFailure
        }
      }
      is RequestResult.ApplicationError -> VerifyResult.UnretryableFailure
    }
  }

  sealed interface VerifyResult {
    /** Successful verification */
    data object Success : VerifyResult

    /** Retryable failure **/
    data class RetryableFailure(val duration: Duration) : VerifyResult

    /** Failure when either the head or the data is corrupted. Retryable if both are reset. */
    data object CorruptedFailure : VerifyResult

    /** Failures that should not be retried. */
    data object UnretryableFailure : VerifyResult
  }
}
