/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.verify

import android.content.Context
import android.content.Intent
import android.text.TextUtils
import androidx.annotation.UiContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx3.asFlow
import org.signal.core.util.concurrent.SignalDispatchers
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.Log
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.fingerprint.Fingerprint
import org.signal.libsignal.protocol.fingerprint.NumericFingerprintGenerator
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil
import org.thoughtcrime.securesms.crypto.ReentrantSessionLock
import org.thoughtcrime.securesms.database.IdentityTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.MultiDeviceVerifiedUpdateJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.LiveRecipient
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.thoughtcrime.securesms.util.IdentityUtil
import java.nio.charset.StandardCharsets

class VerifyDisplayScreenViewModel(
  private val isSafetyNumberVerified: Boolean,
  private val recipientId: RecipientId,
  private val localIdentity: IdentityKey,
  private val remoteIdentity: IdentityKey
) : ViewModel() {

  companion object {
    val TAG = Log.tag(VerifyDisplayScreenViewModel::class.java)
  }

  private val recipient: LiveRecipient = Recipient.live(recipientId)

  private val internalState = MutableStateFlow(
    VerifyDisplayScreenState(
      isSafetyNumberVerified = isSafetyNumberVerified
    )
  )
  val state: StateFlow<VerifyDisplayScreenState> = internalState

  val fingerprintSnapshot: Fingerprint?
    get() {
      return when (val holder = state.value.fingerprintHolder) {
        is FingerprintHolder.Initialised -> holder.fingerprint.fingerprint
        else -> null
      }
    }

  init {
    initializeFingerprints()
    checkAutomaticVerificationEligibility()

    viewModelScope.launch {
      recipient.observable().asFlow().collectLatest { r ->
        internalState.update { it.copy(recipient = r) }
      }
    }
  }

  private fun checkAutomaticVerificationEligibility() {
    if (recipient.get().e164.isEmpty ||
      recipient.get().aci.isEmpty ||
      ProfileKeyUtil.profileKeyOrNull(recipient.get().profileKey) == null ||
      SignalStore.misc.hasKeyTransparencyFailure
    ) {
      internalState.update { it.copy(automaticVerificationStatus = AutomaticVerificationStatus.UNAVAILABLE_PERMANENT) }
    }
  }

  fun setSeenEducationSheet() {
    SignalStore.uiHints.setSeenVerifyAutomaticallySheet()
    internalState.update { it.copy(shouldDisplayVerifyAutomaticallyEducationSheet = false) }
  }

  fun setScannedFingerprint(scanned: String) {
    fingerprintSnapshot?.let { fingerprint ->
      try {
        if (fingerprint.scannableFingerprint.compareTo(scanned.toByteArray(StandardCharsets.ISO_8859_1))) {
          internalState.update { it.copy(scanComparisonResult = VerifyDisplayScreenState.ScanComparisonResult.Success()) }
        } else {
          internalState.update { it.copy(scanComparisonResult = VerifyDisplayScreenState.ScanComparisonResult.Failure()) }
        }
      } catch (e: Exception) {
        Log.w(TAG, e)
        internalState.update { it.copy(scanComparisonResult = VerifyDisplayScreenState.ScanComparisonResult.IncorrectFormat()) }
        return
      }
    }
  }

  @UiContext
  fun createShareIntent(context: Context): Intent {
    return VerifyDisplayRepository.createShareIntent(context, fingerprintSnapshot!!)
  }

  fun copyFingerprintToClipboard() {
    VerifyDisplayRepository.writeToClipboard(fingerprintSnapshot!!)
  }

  fun compareClipboardToFingerprint() {
    val clipboardData = VerifyDisplayRepository.readFromClipboard()
    val fingerprint = (state.value.fingerprintHolder as? FingerprintHolder.Initialised)?.fingerprint

    if (clipboardData == null) {
      internalState.update { it.copy(clipComparisonResult = VerifyDisplayScreenState.ClipComparisonResult.NoDataInClipboard()) }
      return
    }

    val numericClipboardData = clipboardData.replace("\\D".toRegex(), "")
    if (TextUtils.isEmpty(numericClipboardData) || numericClipboardData.length != 60) {
      internalState.update { it.copy(clipComparisonResult = VerifyDisplayScreenState.ClipComparisonResult.NoSafetyNumberInClipboard()) }
      return
    }

    if (fingerprint?.fingerprint?.displayableFingerprint?.displayText == numericClipboardData) {
      internalState.update { it.copy(clipComparisonResult = VerifyDisplayScreenState.ClipComparisonResult.Success()) }
    } else {
      internalState.update { it.copy(clipComparisonResult = VerifyDisplayScreenState.ClipComparisonResult.Failure()) }
    }
  }

  fun verifyAutomatically(canRetry: Boolean = true) {
    viewModelScope.launch(SignalDispatchers.IO) {
      if (internalState.value.automaticVerificationStatus == AutomaticVerificationStatus.UNAVAILABLE_PERMANENT || !isActive) {
        return@launch
      }

      internalState.update { it.copy(automaticVerificationStatus = AutomaticVerificationStatus.VERIFYING) }

      when (val result = VerifySafetyNumberRepository.verifyAutomatically(recipient.get())) {
        VerifySafetyNumberRepository.VerifyResult.Success -> {
          internalState.update { it.copy(automaticVerificationStatus = AutomaticVerificationStatus.VERIFIED) }
        }

        is VerifySafetyNumberRepository.VerifyResult.RetryableFailure -> {
          if (canRetry) {
            delay(result.duration.toMillis())
            verifyAutomatically(canRetry = false)
          } else {
            Log.i(TAG, "Got a retryable exception, but we already retried once. Ignoring.")
            internalState.update { it.copy(automaticVerificationStatus = AutomaticVerificationStatus.UNAVAILABLE_TEMPORARY) }
          }
        }

        VerifySafetyNumberRepository.VerifyResult.CorruptedFailure -> {
          Log.w(TAG, "KT store was corrupted. Clearing everything and starting again.")
          SignalStore.account.distinguishedHead = null
          SignalDatabase.recipients.setKeyTransparencyData(recipient.get().requireAci(), null)
          if (canRetry) {
            verifyAutomatically(canRetry = false)
          } else {
            Log.i(TAG, "Store was corrupted and we can retry, but we already retried once. Ignoring.")
            internalState.update { it.copy(automaticVerificationStatus = AutomaticVerificationStatus.UNAVAILABLE_TEMPORARY) }
          }
        }

        VerifySafetyNumberRepository.VerifyResult.UnretryableFailure -> {
          internalState.update { it.copy(automaticVerificationStatus = AutomaticVerificationStatus.UNAVAILABLE_TEMPORARY) }
        }
      }
    }
  }

  private fun initializeFingerprints() {
    SignalExecutors.UNBOUNDED.execute {
      val resolved = recipient.resolve()

      val generator = NumericFingerprintGenerator(5200)

      var aciFingerprint: SafetyNumberFingerprint? = null

      if (resolved.aci.isPresent) {
        val localIdentifier = SignalStore.account.requireAci().toByteArray()
        val remoteIdentifier = resolved.requireAci().toByteArray()
        val version = 2
        aciFingerprint = SafetyNumberFingerprint(version, localIdentifier, localIdentity, remoteIdentifier, remoteIdentity, generator.createFor(version, localIdentifier, localIdentity, remoteIdentifier, remoteIdentity))
      }

      internalState.update { state -> state.copy(fingerprintHolder = aciFingerprint?.let { FingerprintHolder.Initialised(it) } ?: FingerprintHolder.NoFingerprintAvailable) }
    }
  }

  fun updateSafetyNumberVerification(verified: Boolean) {
    internalState.update { it.copy(isSafetyNumberVerified = verified) }

    val recipientId: RecipientId = recipientId
    val context: Context = AppDependencies.application

    SignalExecutors.BOUNDED.execute {
      val resolved = recipient.resolve()
      if (resolved.aci.isEmpty) {
        Log.w(TAG, "Cannot update safety number verification -- recipient has no ACI")
        return@execute
      }

      ReentrantSessionLock.INSTANCE.acquire().use { _ ->
        if (verified) {
          Log.i(TAG, "Saving identity: $recipientId")
          AppDependencies.protocolStore.aci().identities()
            .saveIdentityWithoutSideEffects(
              recipientId,
              resolved.requireAci(),
              remoteIdentity,
              IdentityTable.VerifiedStatus.VERIFIED,
              false,
              System.currentTimeMillis(),
              true
            )
        } else {
          AppDependencies.protocolStore.aci().identities().setVerified(recipientId, remoteIdentity, IdentityTable.VerifiedStatus.DEFAULT)
        }
        AppDependencies.jobManager
          .add(
            MultiDeviceVerifiedUpdateJob(
              recipientId,
              remoteIdentity,
              if (verified) IdentityTable.VerifiedStatus.VERIFIED else IdentityTable.VerifiedStatus.DEFAULT
            )
          )
        StorageSyncHelper.scheduleSyncForDataChange()
        IdentityUtil.markIdentityVerified(context, recipient.get(), verified, false)
      }
    }
  }

  class Factory(
    private val isSafetyNumberVerified: Boolean,
    private val recipientId: RecipientId,
    private val localIdentity: IdentityKey,
    private val remoteIdentity: IdentityKey
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return modelClass.cast(VerifyDisplayScreenViewModel(isSafetyNumberVerified, recipientId, localIdentity, remoteIdentity))!!
    }
  }
}

data class SafetyNumberFingerprint(
  val version: Int = 0,
  val localStableIdentifier: ByteArray?,
  val localIdentityKey: IdentityKey? = null,
  val remoteStableIdentifier: ByteArray?,
  val remoteIdentityKey: IdentityKey? = null,
  val fingerprint: Fingerprint
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as SafetyNumberFingerprint

    if (version != other.version) return false
    if (localStableIdentifier != null) {
      if (other.localStableIdentifier == null) return false
      if (!localStableIdentifier.contentEquals(other.localStableIdentifier)) return false
    } else if (other.localStableIdentifier != null) {
      return false
    }
    if (localIdentityKey != other.localIdentityKey) return false
    if (remoteStableIdentifier != null) {
      if (other.remoteStableIdentifier == null) return false
      if (!remoteStableIdentifier.contentEquals(other.remoteStableIdentifier)) return false
    } else if (other.remoteStableIdentifier != null) {
      return false
    }
    if (remoteIdentityKey != other.remoteIdentityKey) return false
    if (fingerprint != other.fingerprint) return false

    return true
  }

  override fun hashCode(): Int {
    var result = version
    result = 31 * result + (localStableIdentifier?.contentHashCode() ?: 0)
    result = 31 * result + (localIdentityKey?.hashCode() ?: 0)
    result = 31 * result + (remoteStableIdentifier?.contentHashCode() ?: 0)
    result = 31 * result + (remoteIdentityKey?.hashCode() ?: 0)
    result = 31 * result + fingerprint.hashCode()
    return result
  }
}

sealed interface FingerprintHolder {
  data object Uninitialised : FingerprintHolder
  data object NoFingerprintAvailable : FingerprintHolder
  data class Initialised(val fingerprint: SafetyNumberFingerprint) : FingerprintHolder
}

enum class AutomaticVerificationStatus {
  NONE,
  VERIFYING,
  UNAVAILABLE_PERMANENT,
  UNAVAILABLE_TEMPORARY,
  VERIFIED
}
