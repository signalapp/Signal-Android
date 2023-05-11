package org.thoughtcrime.securesms.jobs

import androidx.annotation.VisibleForTesting
import org.signal.core.util.logging.Log
import org.signal.libsignal.protocol.state.SignalProtocolStore
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.thoughtcrime.securesms.crypto.PreKeyUtil
import org.thoughtcrime.securesms.crypto.storage.PreKeyMetadataStore
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.whispersystems.signalservice.api.push.ServiceId
import org.whispersystems.signalservice.api.push.ServiceIdType
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit

/**
 * Regardless of the current state of affairs with respect to prekeys for either ACI or PNI identities, will
 * attempt to make the state valid.
 *
 * It will rotate/create signed prekeys for both ACI and PNI identities, as well as ensure that the user
 * has a sufficient number of one-time prekeys available on the service.
 */
class PreKeysSyncJob private constructor(parameters: Parameters) : BaseJob(parameters) {

  companion object {
    const val KEY = "PreKeysSyncJob"

    private val TAG = Log.tag(PreKeysSyncJob::class.java)

    /** The minimum number of one-time prekeys we want to the service to have. If we have less than this, refill. */
    private const val ONE_TIME_PREKEY_MINIMUM = 10

    /** How often we want to rotate signed prekeys. */
    @JvmField
    val REFRESH_INTERVAL = 2.days.inWholeMilliseconds

    /** If signed prekeys are older than this, we will require rotation before sending messages. */
    @JvmField
    val MAXIMUM_ALLOWED_SIGNED_PREKEY_AGE = 14.days.inWholeMilliseconds

    @JvmStatic
    fun create(): PreKeysSyncJob {
      return PreKeysSyncJob()
    }

    @JvmStatic
    fun enqueue() {
      ApplicationDependencies.getJobManager().add(create())
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
        val timeSinceLastFullRefresh = System.currentTimeMillis() - SignalStore.misc().lastFullPrekeyRefreshTime

        if (timeSinceLastFullRefresh >= REFRESH_INTERVAL || timeSinceLastFullRefresh < 0) {
          Log.i(TAG, "Scheduling a prekey refresh. Time since last full refresh: $timeSinceLastFullRefresh ms")
          ApplicationDependencies.getJobManager().add(PreKeysSyncJob())
        } else {
          Log.d(TAG, "No prekey job needed. Time since last full refresh: $timeSinceLastFullRefresh ms")
        }
      }
    }
  }

  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  constructor() : this(
    Parameters.Builder()
      .setQueue("PreKeysSyncJob")
      .addConstraint(NetworkConstraint.KEY)
      .setMaxInstancesForFactory(1)
      .setMaxAttempts(Parameters.UNLIMITED)
      .setLifespan(TimeUnit.DAYS.toMillis(30))
      .build()
  )

  override fun getFactoryKey(): String = KEY

  override fun serialize(): ByteArray? = null

  override fun onRun() {
    if (!SignalStore.account().isRegistered || SignalStore.account().aci == null || SignalStore.account().pni == null) {
      warn(TAG, "Not yet registered")
      return
    }

    syncPreKeys(ServiceIdType.ACI, SignalStore.account().aci, ApplicationDependencies.getProtocolStore().aci(), SignalStore.account().aciPreKeys)
    syncPreKeys(ServiceIdType.PNI, SignalStore.account().pni, ApplicationDependencies.getProtocolStore().pni(), SignalStore.account().pniPreKeys)
    SignalStore.misc().lastFullPrekeyRefreshTime = System.currentTimeMillis()
  }

  private fun syncPreKeys(serviceIdType: ServiceIdType, serviceId: ServiceId?, protocolStore: SignalProtocolStore, metadataStore: PreKeyMetadataStore) {
    if (serviceId == null) {
      warn(TAG, serviceIdType, "AccountId not set!")
      return
    }

    val accountManager = ApplicationDependencies.getSignalServiceAccountManager()

    val signedPreKeyRegistered = metadataStore.isSignedPreKeyRegistered && metadataStore.activeSignedPreKeyId >= 0
    val timeSinceLastSignedPreKeyRotation = System.currentTimeMillis() - metadataStore.lastSignedPreKeyRotationTime

    val activeSignedPreKeyRecord: SignedPreKeyRecord = if (!signedPreKeyRegistered || timeSinceLastSignedPreKeyRotation >= REFRESH_INTERVAL) {
      log(serviceIdType, "Rotating signed prekey. SignedPreKeyRegistered: $signedPreKeyRegistered, TimeSinceLastRotation: $timeSinceLastSignedPreKeyRotation ms (${timeSinceLastSignedPreKeyRotation.milliseconds.toDouble(DurationUnit.DAYS)} days)")

      val signedPreKeyRecord: SignedPreKeyRecord = PreKeyUtil.generateAndStoreSignedPreKey(protocolStore, metadataStore)
      accountManager.setSignedPreKey(serviceIdType, signedPreKeyRecord)

      metadataStore.activeSignedPreKeyId = signedPreKeyRecord.id
      metadataStore.isSignedPreKeyRegistered = true
      metadataStore.lastSignedPreKeyRotationTime = System.currentTimeMillis()

      signedPreKeyRecord
    } else {
      log(serviceIdType, "No need to rotate signed prekey. TimeSinceLastRotation: $timeSinceLastSignedPreKeyRotation ms (${timeSinceLastSignedPreKeyRotation.milliseconds.toDouble(DurationUnit.DAYS)} days)")
      protocolStore.loadSignedPreKey(metadataStore.activeSignedPreKeyId)
    }

    val availableOneTimePreKeys = accountManager.getPreKeysCount(serviceIdType)

    if (availableOneTimePreKeys < ONE_TIME_PREKEY_MINIMUM) {
      log(serviceIdType, "There are $availableOneTimePreKeys one-time prekeys available, which is not sufficient. Uploading more.")

      val preKeyRecords = PreKeyUtil.generateAndStoreOneTimePreKeys(protocolStore, metadataStore)
      val identityKey = protocolStore.identityKeyPair
      accountManager.setPreKeys(serviceIdType, identityKey.publicKey, activeSignedPreKeyRecord, preKeyRecords)
    } else {
      log(serviceIdType, "There are $availableOneTimePreKeys one-time prekeys available, which is sufficient. No need to upload.")
    }

    log(serviceIdType, "Cleaning prekeys...")
    PreKeyUtil.cleanSignedPreKeys(protocolStore, metadataStore)
  }

  override fun onShouldRetry(e: Exception): Boolean {
    return when (e) {
      is NonSuccessfulResponseCodeException -> false
      is PushNetworkException -> true
      else -> false
    }
  }

  override fun onFailure() = Unit

  private fun log(serviceIdType: ServiceIdType, message: String) {
    Log.i(TAG, "[$serviceIdType] $message")
  }

  class Factory : Job.Factory<PreKeysSyncJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): PreKeysSyncJob {
      return PreKeysSyncJob(parameters)
    }
  }
}
