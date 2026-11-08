package org.thoughtcrime.securesms.jobs

import androidx.annotation.VisibleForTesting
import arrow.core.raise.Raise
import arrow.core.raise.merge
import org.signal.core.models.ServiceId
import org.signal.core.util.logging.Log
import org.signal.core.util.roundedString
import org.signal.libsignal.net.RequestResult
import org.signal.libsignal.protocol.InvalidKeyException
import org.signal.libsignal.protocol.InvalidKeyIdException
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.libsignal.protocol.kem.KEMPublicKey
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SignalProtocolStore
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.signal.network.api.KeysApiV2
import org.signal.remoteconfig.RemoteConfig
import org.thoughtcrime.securesms.crypto.PreKeyUtil
import org.thoughtcrime.securesms.crypto.storage.PreKeyMetadataStore
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.CoroutineJob
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobs.protos.PreKeysSyncJobData
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.net.SignalNetwork
import org.whispersystems.signalservice.api.SignalServiceAccountDataStore
import org.whispersystems.signalservice.api.push.ServiceIdType
import java.io.IOException
import java.net.ProtocolException
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit
import org.signal.libsignal.protocol.ServiceId as LibSignalServiceId

/**
 * Regardless of the current state of affairs with respect to prekeys for either ACI or PNI identities, will
 * attempt to make the state valid.
 *
 * It will rotate/create signed prekeys for both ACI and PNI identities, as well as ensure that the user
 * has a sufficient number of one-time EC prekeys available on the service.
 *
 * It will also rotate/create last-resort kyber prekeys for both ACI and PNI identities, as well as ensure
 * that the user has a sufficient number of one-time kyber prekeys available on the service.
 */
class PreKeysSyncJob private constructor(
  parameters: Parameters,
  private val forceRotationRequested: Boolean
) : CoroutineJob(parameters) {

  companion object {
    const val KEY = "PreKeysSyncJob"

    private val TAG = Log.tag(PreKeysSyncJob::class.java)

    /** The minimum number of one-time prekeys we want to the service to have. If we have less than this, refill. Applies to both EC and kyber prekeys. */
    private const val ONE_TIME_PREKEY_MINIMUM = 10

    /** How often we want to rotate signed prekeys and last-resort kyber prekeys. */
    @JvmField
    val REFRESH_INTERVAL = 2.days.inWholeMilliseconds

    /** If signed prekeys or last-resort kyber keys are older than this, we will require rotation before sending messages. */
    @JvmField
    val MAXIMUM_ALLOWED_SIGNED_PREKEY_AGE = 14.days.inWholeMilliseconds

    /**
     * @param forceRotationRequested If true, this will force the rotation of all keys, provided we haven't already done a forced refresh recently.
     */
    @JvmOverloads
    @JvmStatic
    fun create(forceRotationRequested: Boolean = false): PreKeysSyncJob {
      return PreKeysSyncJob(forceRotationRequested)
    }

    @JvmStatic
    fun enqueue() {
      AppDependencies.jobManager.add(create())
    }

    @JvmStatic
    fun enqueueIfNeeded() {
      if (!SignalStore.account.aciPreKeys.isSignedPreKeyRegistered || !SignalStore.account.pniPreKeys.isSignedPreKeyRegistered) {
        Log.i(TAG, "Some signed/last-resort prekeys aren't registered yet. Enqueuing a job. ACI: ${SignalStore.account.aciPreKeys.isSignedPreKeyRegistered} PNI: ${SignalStore.account.pniPreKeys.isSignedPreKeyRegistered}")
        AppDependencies.jobManager.add(PreKeysSyncJob())
      } else if (SignalStore.account.aciPreKeys.activeSignedPreKeyId < 0 || SignalStore.account.pniPreKeys.activeSignedPreKeyId < 0) {
        Log.i(TAG, "Some signed prekeys aren't active yet. Enqueuing a job. ACI: ${SignalStore.account.aciPreKeys.activeSignedPreKeyId >= 0} PNI: ${SignalStore.account.pniPreKeys.activeSignedPreKeyId >= 0}")
        AppDependencies.jobManager.add(PreKeysSyncJob())
      } else if (SignalStore.account.aciPreKeys.lastResortKyberPreKeyId < 0 || SignalStore.account.pniPreKeys.lastResortKyberPreKeyId < 0) {
        Log.i(TAG, "Some last-resort kyber prekeys aren't active yet. Enqueuing a job. ACI: ${SignalStore.account.aciPreKeys.lastResortKyberPreKeyId >= 0} PNI: ${SignalStore.account.pniPreKeys.lastResortKyberPreKeyId >= 0}")
        AppDependencies.jobManager.add(PreKeysSyncJob())
      } else {
        val timeSinceLastFullRefresh = System.currentTimeMillis() - SignalStore.misc.lastFullPrekeyRefreshTime

        if (timeSinceLastFullRefresh >= REFRESH_INTERVAL || timeSinceLastFullRefresh < 0) {
          Log.i(TAG, "Scheduling a prekey refresh. Time since last full refresh: $timeSinceLastFullRefresh ms")
          AppDependencies.jobManager.add(PreKeysSyncJob())
        } else {
          Log.d(TAG, "No prekey job needed. Time since last full refresh: $timeSinceLastFullRefresh ms")
        }
      }
    }
  }

  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  constructor(forceRotation: Boolean = false) : this(
    Parameters.Builder()
      .setQueue("PreKeysSyncJob")
      .addConstraint(NetworkConstraint.KEY)
      .apply {
        if (forceRotation) {
          setMaxInstancesForFactory(Parameters.UNLIMITED)
          setMaxInstancesForQueue(2)
        } else {
          setMaxInstancesForFactory(1)
        }
      }
      .setMaxAttempts(Parameters.UNLIMITED)
      .setLifespan(TimeUnit.DAYS.toMillis(30))
      .build(),
    forceRotation
  )

  override fun getFactoryKey(): String = KEY

  override fun serialize(): ByteArray {
    return PreKeysSyncJobData(forceRotationRequested).encode()
  }

  override suspend fun doRun(): Result = merge {
    doWork()
    Result.success()
  }

  private suspend fun Raise<Result>.doWork() {
    if (!SignalStore.account.isRegistered || SignalStore.account.aci == null) {
      Log.w(TAG, "Not yet registered")
      return
    }

    val forceRotation = shouldForceRotation(forceRotationRequested)

    val pniRotationOverride = SignalStore.misc.forcePniSignedPreKeyRotation
    if (pniRotationOverride) {
      warn(ServiceIdType.PNI, "Forced PNI prekey rotation pending after PniChangeNumber sync. Bypassing dedup/interval gating for PNI.")
    }

    val forcePniRotation = forceRotation || pniRotationOverride

    if (forcePniRotation) {
      Log.w(TAG, "Forcing prekey rotation. ACI=$forceRotation PNI=$forcePniRotation")
    } else if (forceRotationRequested) {
      Log.w(TAG, "Forced prekey rotation was requested, but we already did a forced refresh ${System.currentTimeMillis() - SignalStore.misc.lastForcedPreKeyRefresh} ms ago. Ignoring.")
    }

    val preKeyCounts = bindOrRetry(SignalNetwork.keysApiV2.getPreKeyCounts(), "fetching prekey counts")

    syncPreKeys(ServiceIdType.ACI, SignalStore.account.aci, AppDependencies.protocolStore.aci(), SignalStore.account.aciPreKeys, preKeyCounts.aciEcPreKeyCount, preKeyCounts.aciKemPreKeyCount, forceRotation)

    val pniProtocolStore = AppDependencies.protocolStore.pniOrNull()
    if (pniProtocolStore != null) {
      syncPreKeys(ServiceIdType.PNI, SignalStore.account.pni, pniProtocolStore, SignalStore.account.pniPreKeys, preKeyCounts.pniEcPreKeyCount, preKeyCounts.pniKemPreKeyCount, forcePniRotation)
    } else {
      warn(ServiceIdType.PNI, "No PNI store. Skipping PNI prekey sync.")
    }

    SignalStore.misc.lastFullPrekeyRefreshTime = System.currentTimeMillis()

    if (forcePniRotation) {
      SignalStore.misc.lastForcedPreKeyRefresh = System.currentTimeMillis()
    }

    if (pniRotationOverride) {
      // Cleared only after both syncPreKeys calls succeeded. A failed upload leaves the flag set for the next attempt.
      SignalStore.misc.forcePniSignedPreKeyRotation = false
    }
  }

  private suspend fun Raise<Result>.syncPreKeys(
    serviceIdType: ServiceIdType,
    serviceId: ServiceId?,
    protocolStore: SignalServiceAccountDataStore,
    metadataStore: PreKeyMetadataStore,
    oneTimeEcPreKeyCount: Int,
    oneTimeKyberPreKeyCount: Int,
    forceRotation: Boolean
  ) {
    if (serviceId == null) {
      warn(serviceIdType, "AccountId not set!")
      return
    }

    val signedPreKeyToUpload: SignedPreKeyRecord? = createSignedPreKeyForUploadingIfNeeded(serviceIdType, protocolStore, metadataStore, forceRotation)

    val oneTimeEcPreKeysToUpload: List<PreKeyRecord>? = if (forceRotation || oneTimeEcPreKeyCount < ONE_TIME_PREKEY_MINIMUM) {
      log(serviceIdType, "There are $oneTimeEcPreKeyCount one-time EC prekeys available, which is less than our threshold. Need more. (Forced: $forceRotation)")
      PreKeyUtil.generateAndStoreOneTimeEcPreKeys(protocolStore, metadataStore)
    } else {
      log(serviceIdType, "There are $oneTimeEcPreKeyCount one-time EC prekeys available, which is enough.")
      null
    }

    val lastResortKyberPreKeyToUpload: KyberPreKeyRecord? = lastResortKyberPreKeyUploadIfNeeded(serviceIdType, protocolStore, metadataStore, forceRotation)

    val oneTimeKyberPreKeysToUpload: List<KyberPreKeyRecord>? = if (forceRotation || oneTimeKyberPreKeyCount < ONE_TIME_PREKEY_MINIMUM) {
      log(serviceIdType, "There are $oneTimeKyberPreKeyCount one-time kyber prekeys available, which is less than our threshold. Need more. (Forced: $forceRotation)")
      PreKeyUtil.generateAndStoreOneTimeKyberPreKeys(protocolStore, metadataStore)
    } else {
      log(serviceIdType, "There are $oneTimeKyberPreKeyCount one-time kyber prekeys available, which is enough.")
      null
    }

    if (signedPreKeyToUpload != null || oneTimeEcPreKeysToUpload != null || lastResortKyberPreKeyToUpload != null || oneTimeKyberPreKeysToUpload != null) {
      log(serviceIdType, "Something to upload. SignedPreKey: ${signedPreKeyToUpload != null}, OneTimeEcPreKeys: ${oneTimeEcPreKeysToUpload != null}, LastResortKyberPreKey: ${lastResortKyberPreKeyToUpload != null}, OneTimeKyberPreKeys: ${oneTimeKyberPreKeysToUpload != null}")

      if (oneTimeEcPreKeysToUpload != null) {
        bindOrRetry(SignalNetwork.keysApiV2.setOneTimeEcPreKeys(serviceIdType.toLibSignalKind(), oneTimeEcPreKeysToUpload), "uploading one-time EC prekeys", serviceIdType)
        log(serviceIdType, "Successfully uploaded one-time EC prekeys.")
      }

      if (oneTimeKyberPreKeysToUpload != null) {
        bindOrRetry(SignalNetwork.keysApiV2.setOneTimeKemPreKeys(serviceIdType.toLibSignalKind(), oneTimeKyberPreKeysToUpload), "uploading one-time kyber prekeys", serviceIdType)
        log(serviceIdType, "Successfully uploaded one-time kyber prekeys.")
      }

      // Repeated-use keys go last: until both land, the consistency check keeps failing, so a retry after a partial failure is still forced.
      if (signedPreKeyToUpload != null) {
        bindOrRetry(SignalNetwork.keysApiV2.setSignedEcPreKey(serviceIdType.toLibSignalKind(), signedPreKeyToUpload), "uploading signed prekey", serviceIdType)
        log(serviceIdType, "Successfully uploaded signed prekey.")
        metadataStore.activeSignedPreKeyId = signedPreKeyToUpload.id
        metadataStore.isSignedPreKeyRegistered = true
        metadataStore.lastSignedPreKeyRotationTime = System.currentTimeMillis()
      }

      if (lastResortKyberPreKeyToUpload != null) {
        bindOrRetry(SignalNetwork.keysApiV2.setLastResortKemPreKey(serviceIdType.toLibSignalKind(), lastResortKyberPreKeyToUpload), "uploading last-resort kyber prekey", serviceIdType)
        log(serviceIdType, "Successfully uploaded last-resort kyber prekey.")
        metadataStore.lastResortKyberPreKeyId = lastResortKyberPreKeyToUpload.id
        metadataStore.lastResortKyberPreKeyRotationTime = System.currentTimeMillis()
      }
    } else {
      log(serviceIdType, "No prekeys to upload.")
    }

    log(serviceIdType, "Cleaning prekeys...")
    PreKeyUtil.cleanSignedPreKeys(protocolStore, metadataStore)
    PreKeyUtil.cleanLastResortKyberPreKeys(protocolStore, metadataStore)
    PreKeyUtil.cleanOneTimePreKeys(protocolStore)
  }

  private fun createSignedPreKeyForUploadingIfNeeded(serviceIdType: ServiceIdType, protocolStore: SignalProtocolStore, metadataStore: PreKeyMetadataStore, forceRotation: Boolean): SignedPreKeyRecord? {
    val signedPreKeyRegistered = metadataStore.isSignedPreKeyRegistered && metadataStore.activeSignedPreKeyId >= 0
    val timeSinceLastSignedPreKeyRotation = System.currentTimeMillis() - metadataStore.lastSignedPreKeyRotationTime

    return if (forceRotation || !signedPreKeyRegistered || timeSinceLastSignedPreKeyRotation >= REFRESH_INTERVAL || timeSinceLastSignedPreKeyRotation < 0) {
      log(serviceIdType, "Rotating signed prekey. ForceRotation: $forceRotation, SignedPreKeyRegistered: $signedPreKeyRegistered, TimeSinceLastRotation: $timeSinceLastSignedPreKeyRotation ms (${timeSinceLastSignedPreKeyRotation.milliseconds.toDouble(DurationUnit.DAYS).roundedString(2)} days)")
      PreKeyUtil.generateAndStoreSignedPreKey(protocolStore, metadataStore)
    } else {
      log(serviceIdType, "No need to rotate signed prekey. TimeSinceLastRotation: $timeSinceLastSignedPreKeyRotation ms (${timeSinceLastSignedPreKeyRotation.milliseconds.toDouble(DurationUnit.DAYS).roundedString(2)} days)")
      null
    }
  }

  private fun lastResortKyberPreKeyUploadIfNeeded(serviceIdType: ServiceIdType, protocolStore: SignalServiceAccountDataStore, metadataStore: PreKeyMetadataStore, forceRotation: Boolean): KyberPreKeyRecord? {
    val lastResortRegistered = metadataStore.lastResortKyberPreKeyId >= 0
    val timeSinceLastSignedPreKeyRotation = System.currentTimeMillis() - metadataStore.lastResortKyberPreKeyRotationTime

    return if (forceRotation || !lastResortRegistered || timeSinceLastSignedPreKeyRotation >= REFRESH_INTERVAL || timeSinceLastSignedPreKeyRotation < 0) {
      log(serviceIdType, "Rotating last-resort kyber prekey. ForceRotation: $forceRotation, TimeSinceLastRotation: $timeSinceLastSignedPreKeyRotation ms (${timeSinceLastSignedPreKeyRotation.milliseconds.toDouble(DurationUnit.DAYS).roundedString(2)} days)")
      PreKeyUtil.generateAndStoreLastResortKyberPreKey(protocolStore, metadataStore)
    } else {
      log(serviceIdType, "No need to rotate last-resort kyber prekey. TimeSinceLastRotation: $timeSinceLastSignedPreKeyRotation ms (${timeSinceLastSignedPreKeyRotation.milliseconds.toDouble(DurationUnit.DAYS).roundedString(2)} days)")
      null
    }
  }

  private suspend fun Raise<Result>.shouldForceRotation(forceRotationRequested: Boolean): Boolean {
    if (!forceRotationRequested) {
      return false
    }

    Log.w(TAG, "Forced rotation was requested.")
    warn(ServiceIdType.ACI, "Active Signed EC: ${SignalStore.account.aciPreKeys.activeSignedPreKeyId}, Last Resort Kyber: ${SignalStore.account.aciPreKeys.lastResortKyberPreKeyId}")
    warn(ServiceIdType.PNI, "Active Signed EC: ${SignalStore.account.pniPreKeys.activeSignedPreKeyId}, Last Resort Kyber: ${SignalStore.account.pniPreKeys.lastResortKyberPreKeyId}")

    if (!isPreKeyConsistent(ServiceIdType.ACI, AppDependencies.protocolStore.aci(), SignalStore.account.aciPreKeys)) {
      warn(ServiceIdType.ACI, "Prekey consistency check failed! Must rotate keys!")
      return true
    }

    val pniStore = AppDependencies.protocolStore.pniOrNull()
    if (pniStore != null && !isPreKeyConsistent(ServiceIdType.PNI, pniStore, SignalStore.account.pniPreKeys)) {
      warn(ServiceIdType.PNI, "Prekey consistency check failed! Must rotate keys! (ACI consistency check must have passed)")
      return true
    }

    Log.w(TAG, "Forced rotation was requested, but the consistency checks passed!")
    val timeSinceLastForcedRotation = System.currentTimeMillis() - SignalStore.misc.lastForcedPreKeyRefresh
    // We check < 0 in case someone changed their clock and had a bad value set
    return timeSinceLastForcedRotation > RemoteConfig.preKeyForceRefreshInterval || timeSinceLastForcedRotation < 0
  }

  private suspend fun Raise<Result>.isPreKeyConsistent(serviceIdType: ServiceIdType, protocolStore: SignalServiceAccountDataStore, metadataStore: PreKeyMetadataStore): Boolean {
    val signedPreKey: ECPublicKey
    val lastResortKyberPreKey: KEMPublicKey
    try {
      signedPreKey = protocolStore.loadSignedPreKey(metadataStore.activeSignedPreKeyId).keyPair.publicKey
      lastResortKyberPreKey = protocolStore.loadKyberPreKey(metadataStore.lastResortKyberPreKeyId).keyPair.publicKey
    } catch (e: InvalidKeyException) {
      Log.w(TAG, "Unable to load keys. Reporting as mismatch.", e)
      return false
    } catch (e: InvalidKeyIdException) {
      Log.w(TAG, "Unable to load keys. Reporting as mismatch.", e)
      return false
    }

    val result = SignalNetwork.keysApiV2.checkRepeatedUseKeysConsistency(
      serviceIdType = serviceIdType.toLibSignalKind(),
      identityKey = protocolStore.identityKeyPair.publicKey,
      signedPreKeyId = metadataStore.activeSignedPreKeyId,
      signedPreKey = signedPreKey,
      lastResortKyberKeyId = metadataStore.lastResortKyberPreKeyId,
      lastResortKyberKey = lastResortKyberPreKey
    )

    return when (result) {
      is RequestResult.Success -> {
        true
      }
      is RequestResult.NonSuccess -> {
        when (result.error) {
          is KeysApiV2.CheckRepeatedUseKeysError.Mismatch -> {
            Log.w(TAG, "Server indicated that our keys do not match!")
          }
          is KeysApiV2.CheckRepeatedUseKeysError.UnknownError -> {
            Log.w(TAG, "Non-successful response from checking repeated use: ${result.error}")
          }
        }
        false
      }
      is RequestResult.RetryableNetworkError -> {
        warn(serviceIdType, "Network error checking prekey consistency. Retrying.", result.networkError)
        raise(Result.retry(result.retryAfter?.toMillis() ?: defaultBackoff()))
      }
      is RequestResult.ApplicationError -> raise(Result.fatalFailure(RuntimeException(result.cause)))
    }
  }

  private fun <T> Raise<Result>.bindOrRetry(result: RequestResult<T, Nothing>, description: String, serviceIdType: ServiceIdType? = null): T {
    return when (result) {
      is RequestResult.Success -> result.result
      is RequestResult.NonSuccess -> result.error
      is RequestResult.RetryableNetworkError -> {
        val message = "Network error $description. Retrying."
        if (serviceIdType != null) {
          warn(serviceIdType, message, result.networkError)
        } else {
          Log.w(TAG, message, result.networkError)
        }
        raise(Result.retry(result.retryAfter?.toMillis() ?: defaultBackoff()))
      }
      is RequestResult.ApplicationError -> raise(Result.fatalFailure(RuntimeException(result.cause)))
    }
  }

  override fun onFailure() {
    Log.w(TAG, "Failed to sync prekeys. Enqueuing an account consistency check.")
    AppDependencies.jobManager.add(AccountConsistencyWorkerJob())
  }

  private fun ServiceIdType.toLibSignalKind(): LibSignalServiceId.Kind {
    return when (this) {
      ServiceIdType.ACI -> LibSignalServiceId.Kind.ACI
      ServiceIdType.PNI -> LibSignalServiceId.Kind.PNI
    }
  }

  private fun log(serviceIdType: ServiceIdType, message: String) {
    Log.i(TAG, "[$serviceIdType] $message")
  }

  private fun warn(serviceIdType: ServiceIdType, message: String, throwable: Throwable? = null) {
    Log.w(TAG, "[$serviceIdType] $message", throwable)
  }

  class Factory : Job.Factory<PreKeysSyncJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): PreKeysSyncJob {
      return try {
        serializedData?.let {
          val data = PreKeysSyncJobData.ADAPTER.decode(serializedData)
          PreKeysSyncJob(parameters, data.forceRefreshRequested)
        } ?: PreKeysSyncJob(parameters, forceRotationRequested = false)
      } catch (e: IOException) {
        Log.w(TAG, "Error deserializing PreKeysSyncJob", e)
        PreKeysSyncJob(parameters, forceRotationRequested = false)
      } catch (e: ProtocolException) {
        Log.w(TAG, "Error deserializing PreKeysSyncJob", e)
        PreKeysSyncJob(parameters, forceRotationRequested = false)
      }
    }
  }
}
