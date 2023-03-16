package org.thoughtcrime.securesms.jobs

import androidx.annotation.VisibleForTesting
import org.signal.core.util.logging.Log
import org.signal.libsignal.protocol.state.SignalProtocolStore
import org.thoughtcrime.securesms.crypto.PreKeyUtil
import org.thoughtcrime.securesms.crypto.storage.PreKeyMetadataStore
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.JsonJobData
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.signalservice.api.push.ServiceId
import org.whispersystems.signalservice.api.push.ServiceIdType
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException
import java.util.concurrent.TimeUnit

/**
 * Regardless of the current state of affairs with respect to prekeys for either ACI or PNI identities, will
 * attempt to make the state valid.
 *
 * If prekeys aren't registered for an identity they will be created.
 *
 * If prekeys are registered but the count is below the minimum threshold, then new ones will be uploaded.
 */
class PreKeysSyncJob private constructor(private val forceRotate: Boolean = false, parameters: Parameters) : BaseJob(parameters) {

  companion object {
    const val KEY = "PreKeysSyncJob"

    private val TAG = Log.tag(PreKeysSyncJob::class.java)
    private val KEY_FORCE_ROTATE = "force_rotate"
    private const val PREKEY_MINIMUM = 10
    private val REFRESH_INTERVAL = TimeUnit.DAYS.toMillis(3)

    fun create(forceRotate: Boolean = false): PreKeysSyncJob {
      return PreKeysSyncJob(forceRotate)
    }

    @JvmStatic
    @JvmOverloads
    fun enqueue(forceRotate: Boolean = false) {
      ApplicationDependencies.getJobManager().add(create(forceRotate))
    }

    @JvmStatic
    fun enqueueIfNeeded() {
      if (!SignalStore.account().aciPreKeys.isSignedPreKeyRegistered || !SignalStore.account().pniPreKeys.isSignedPreKeyRegistered) {
        Log.i(TAG, "Some signed prekeys aren't registered yet. Enqueuing a job. ACI: ${SignalStore.account().aciPreKeys.isSignedPreKeyRegistered} PNI: ${SignalStore.account().pniPreKeys.isSignedPreKeyRegistered}")
        ApplicationDependencies.getJobManager().add(PreKeysSyncJob())
      } else if (SignalStore.account().aciPreKeys.activeSignedPreKeyId < 0 || SignalStore.account().pniPreKeys.activeSignedPreKeyId < 0) {
        Log.i(TAG, "Some signed prekeys aren't active yet. Enqueuing a job. ACI: ${SignalStore.account().aciPreKeys.activeSignedPreKeyId >= 0} PNI: ${SignalStore.account().pniPreKeys.activeSignedPreKeyId >= 0}")
        ApplicationDependencies.getJobManager().add(PreKeysSyncJob())
      } else {
        val timeSinceLastRefresh = System.currentTimeMillis() - SignalStore.misc().lastPrekeyRefreshTime

        if (timeSinceLastRefresh > REFRESH_INTERVAL) {
          Log.i(TAG, "Scheduling a prekey refresh. Time since last schedule: $timeSinceLastRefresh ms")
          ApplicationDependencies.getJobManager().add(PreKeysSyncJob())
        }
      }
    }
  }

  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  constructor(forceRotate: Boolean = false) : this(
    forceRotate,
    Parameters.Builder()
      .setQueue("PreKeysSyncJob")
      .addConstraint(NetworkConstraint.KEY)
      .setMaxInstancesForFactory(1)
      .setMaxAttempts(Parameters.UNLIMITED)
      .setLifespan(TimeUnit.DAYS.toMillis(30))
      .build()
  )

  override fun getFactoryKey(): String = KEY

  override fun serialize(): ByteArray? {
    return JsonJobData.Builder()
      .putBoolean(KEY_FORCE_ROTATE, forceRotate)
      .serialize()
  }

  override fun onRun() {
    if (!SignalStore.account().isRegistered || SignalStore.account().aci == null || SignalStore.account().pni == null) {
      warn(TAG, "Not yet registered")
      return
    }

    syncPreKeys(ServiceIdType.ACI, SignalStore.account().aci, ApplicationDependencies.getProtocolStore().aci(), SignalStore.account().aciPreKeys)
    syncPreKeys(ServiceIdType.PNI, SignalStore.account().pni, ApplicationDependencies.getProtocolStore().pni(), SignalStore.account().pniPreKeys)
    SignalStore.misc().lastPrekeyRefreshTime = System.currentTimeMillis()
  }

  private fun syncPreKeys(serviceIdType: ServiceIdType, serviceId: ServiceId?, protocolStore: SignalProtocolStore, metadataStore: PreKeyMetadataStore) {
    if (serviceId == null) {
      warn(TAG, serviceIdType, "AccountId not set!")
      return
    }

    if (metadataStore.isSignedPreKeyRegistered && metadataStore.activeSignedPreKeyId >= 0) {
      if (forceRotate || System.currentTimeMillis() > TextSecurePreferences.getSignedPreKeyRotationTime(context) || metadataStore.signedPreKeyFailureCount > 5) {
        log(serviceIdType, "Rotating signed prekey...")
        rotateSignedPreKey(serviceIdType, protocolStore, metadataStore)
      } else {
        log(serviceIdType, "Refreshing prekeys...")
        refreshKeys(serviceIdType, protocolStore, metadataStore)
      }
    } else {
      log(serviceIdType, "Creating signed prekey...")
      rotateSignedPreKey(serviceIdType, protocolStore, metadataStore)
    }
  }

  private fun rotateSignedPreKey(serviceIdType: ServiceIdType, protocolStore: SignalProtocolStore, metadataStore: PreKeyMetadataStore) {
    val signedPreKeyRecord = PreKeyUtil.generateAndStoreSignedPreKey(protocolStore, metadataStore)
    ApplicationDependencies.getSignalServiceAccountManager().setSignedPreKey(serviceIdType, signedPreKeyRecord)

    metadataStore.activeSignedPreKeyId = signedPreKeyRecord.id
    metadataStore.isSignedPreKeyRegistered = true
    metadataStore.signedPreKeyFailureCount = 0
  }

  private fun refreshKeys(serviceIdType: ServiceIdType, protocolStore: SignalProtocolStore, metadataStore: PreKeyMetadataStore) {
    val accountManager = ApplicationDependencies.getSignalServiceAccountManager()
    val availableKeys = accountManager.getPreKeysCount(serviceIdType)

    log(serviceIdType, "Available keys: $availableKeys")

    if (availableKeys >= PREKEY_MINIMUM && metadataStore.isSignedPreKeyRegistered) {
      log(serviceIdType, "Available keys sufficient.")
      return
    }

    val preKeyRecords = PreKeyUtil.generateAndStoreOneTimePreKeys(protocolStore, metadataStore)
    val signedPreKeyRecord = PreKeyUtil.generateAndStoreSignedPreKey(protocolStore, metadataStore)
    val identityKey = protocolStore.identityKeyPair

    log(serviceIdType, "Registering new prekeys...")

    accountManager.setPreKeys(serviceIdType, identityKey.publicKey, signedPreKeyRecord, preKeyRecords)
    metadataStore.activeSignedPreKeyId = signedPreKeyRecord.id
    metadataStore.isSignedPreKeyRegistered = true

    log(serviceIdType, "Cleaning prekeys...")
    PreKeyUtil.cleanSignedPreKeys(protocolStore, metadataStore)

    SignalStore.misc().lastPrekeyRefreshTime = System.currentTimeMillis()
    log(serviceIdType, "Successfully refreshed prekeys.")
  }

  override fun onShouldRetry(e: Exception): Boolean {
    return when (e) {
      is NonSuccessfulResponseCodeException -> false
      is PushNetworkException -> true
      else -> false
    }
  }

  override fun onFailure() {
    val aciStore = SignalStore.account().aciPreKeys
    val pniStore = SignalStore.account().pniPreKeys

    if ((aciStore.isSignedPreKeyRegistered || pniStore.isSignedPreKeyRegistered) && forceRotate) {
      aciStore.signedPreKeyFailureCount++
      pniStore.signedPreKeyFailureCount++
    }
  }

  private fun log(serviceIdType: ServiceIdType, message: String) {
    Log.i(TAG, "[$serviceIdType] $message")
  }

  class Factory : Job.Factory<PreKeysSyncJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): PreKeysSyncJob {
      val data = JsonJobData.deserialize(serializedData)
      return PreKeysSyncJob(data.getBooleanOrDefault(KEY_FORCE_ROTATE, false), parameters)
    }
  }
}
