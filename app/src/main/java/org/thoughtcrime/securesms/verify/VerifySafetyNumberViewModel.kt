/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.verify

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.Log
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.fingerprint.Fingerprint
import org.signal.libsignal.protocol.fingerprint.NumericFingerprintGenerator
import org.thoughtcrime.securesms.crypto.ReentrantSessionLock
import org.thoughtcrime.securesms.database.IdentityTable
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.MultiDeviceVerifiedUpdateJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.LiveRecipient
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.thoughtcrime.securesms.util.IdentityUtil

class VerifySafetyNumberViewModel(
  private val recipientId: RecipientId,
  private val localIdentity: IdentityKey,
  private val remoteIdentity: IdentityKey
) : ViewModel() {

  companion object {
    val TAG = Log.tag(VerifySafetyNumberViewModel::class.java)
  }

  val recipient: LiveRecipient = Recipient.live(recipientId)

  private val fingerprintListLiveData = MutableLiveData<List<SafetyNumberFingerprint>>()

  init {
    initializeFingerprints()
  }

  private fun initializeFingerprints() {
    SignalExecutors.UNBOUNDED.execute {
      val resolved = recipient.resolve()

      val fingerprintList: MutableList<SafetyNumberFingerprint> = ArrayList(2)
      val generator = NumericFingerprintGenerator(5200)

      var aciFingerprint: SafetyNumberFingerprint? = null

      if (resolved.aci.isPresent) {
        val localIdentifier = SignalStore.account.requireAci().toByteArray()
        val remoteIdentifier = resolved.requireAci().toByteArray()
        val version = 2
        aciFingerprint = SafetyNumberFingerprint(version, localIdentifier, localIdentity, remoteIdentifier, remoteIdentity, generator.createFor(version, localIdentifier, localIdentity, remoteIdentifier, remoteIdentity))
      }

      if (aciFingerprint != null) {
        fingerprintList.add(aciFingerprint)
      }

      fingerprintListLiveData.postValue(fingerprintList)
    }
  }

  fun getFingerprints(): LiveData<List<SafetyNumberFingerprint>> {
    return fingerprintListLiveData
  }

  fun updateSafetyNumberVerification(verified: Boolean) {
    val recipientId: RecipientId = recipientId
    val context: Context = AppDependencies.application

    SignalExecutors.BOUNDED.execute {
      ReentrantSessionLock.INSTANCE.acquire().use { _ ->
        if (verified) {
          Log.i(TAG, "Saving identity: $recipientId")
          AppDependencies.protocolStore.aci().identities()
            .saveIdentityWithoutSideEffects(
              recipientId,
              recipient.resolve().requireAci(),
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
    private val recipientId: RecipientId,
    private val localIdentity: IdentityKey,
    private val remoteIdentity: IdentityKey
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return modelClass.cast(VerifySafetyNumberViewModel(recipientId, localIdentity, remoteIdentity))!!
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
    result = 31 * result + (fingerprint?.hashCode() ?: 0)
    return result
  }
}
