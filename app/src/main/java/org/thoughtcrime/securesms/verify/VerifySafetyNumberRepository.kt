package org.thoughtcrime.securesms.verify

import org.signal.core.util.logging.Log
import org.signal.libsignal.net.KeyTransparency.CheckMode
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
   * Given a recipient will try to verify via key transparency.
   */
  suspend fun verifyAutomatically(recipient: Recipient): VerifyResult {
    val profileKey = ProfileKeyUtil.profileKeyOrNull(recipient.profileKey)
    val identityRecord = AppDependencies.protocolStore.aci().identities().getIdentityRecord(recipient.id)

    if (recipient.aci.isEmpty || recipient.e164.isEmpty || profileKey == null || identityRecord.isEmpty) {
      Log.w(TAG, "Unable to verify automatically because of missing aci, e164, profile key, or identity record.")
      return VerifyResult.UnretryableFailure
    }

    val aciIdentityKey = identityRecord.get().identityKey

    val result = SignalNetwork.keyTransparency.check(
      checkMode = CheckMode.Contact,
      aci = recipient.requireAci().libSignalAci,
      aciIdentityKey = aciIdentityKey,
      e164 = recipient.requireE164(),
      unidentifiedAccessKey = profileKey.let { UnidentifiedAccess.deriveAccessKeyFrom(it) },
      usernameHash = null,
      keyTransparencyStore = KeyTransparencyStore
    )

    Log.i(TAG, "Key transparency complete, result: $result")
    return when (result) {
      is RequestResult.Success -> {
        VerifyResult.Success
      }
      is RequestResult.NonSuccess -> {
        VerifyResult.UnretryableFailure
      }
      is RequestResult.RetryableNetworkError -> {
        if (result.retryAfter != null) {
          VerifyResult.RetryableFailure(result.retryAfter!!)
        } else {
          VerifyResult.UnretryableFailure
        }
      }
      is RequestResult.ApplicationError -> {
        if (result.cause is IllegalArgumentException) {
          VerifyResult.CorruptedFailure
        } else {
          VerifyResult.UnretryableFailure
        }
      }
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
