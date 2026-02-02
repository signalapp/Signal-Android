package org.thoughtcrime.securesms.jobs

import org.signal.core.util.logging.Log
import org.signal.libsignal.keytrans.KeyTransparencyException
import org.signal.libsignal.keytrans.VerificationFailedException
import org.signal.libsignal.net.AppExpiredException
import org.signal.libsignal.net.KeyTransparency
import org.signal.libsignal.net.RequestResult
import org.signal.libsignal.usernames.Username
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.KeyTransparencyStore
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.CoroutineJob
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobs.protos.CheckKeyTransparencyJobData
import org.thoughtcrime.securesms.keyvalue.AccountValues
import org.thoughtcrime.securesms.keyvalue.PhoneNumberPrivacyValues.PhoneNumberDiscoverabilityMode
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.net.SignalNetwork
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.RemoteConfig
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/**
 * Checks verification of our own identifiers using key transparency.
 */
class CheckKeyTransparencyJob private constructor(
  private val showFailure: Boolean,
  parameters: Parameters
) : CoroutineJob(parameters) {

  companion object {
    private val TAG = Log.tag(CheckKeyTransparencyJob::class)
    const val KEY = "CheckKeyTransparencyJob"

    private val TIME_BETWEEN_CHECK = 7.days

    @JvmStatic
    fun enqueueIfNecessary() {
      if (!canRunJob()) {
        return
      }

      val nextCheckIn = SignalStore.misc.lastKeyTransparencyTime.milliseconds + TIME_BETWEEN_CHECK

      if (nextCheckIn.inWholeMilliseconds < System.currentTimeMillis()) {
        AppDependencies.jobManager.add(
          CheckKeyTransparencyJob(
            showFailure = false,
            parameters = Parameters.Builder()
              .addConstraint(NetworkConstraint.KEY)
              .setInitialDelay(5.minutes.inWholeMilliseconds)
              .setGlobalPriority(Parameters.PRIORITY_LOWER)
              .setMaxInstancesForFactory(2)
              .build()
          )
        )
      }
    }

    /**
     * Following a failure, runs another job that will now show an error if it fails again.
     */
    fun enqueueFollowingFailure() {
      if (!canRunJob()) {
        return
      }

      AppDependencies.jobManager.add(
        CheckKeyTransparencyJob(
          showFailure = true,
          parameters = Parameters.Builder()
            .addConstraint(NetworkConstraint.KEY)
            .setInitialDelay(1.days.inWholeMilliseconds)
            .setGlobalPriority(Parameters.PRIORITY_LOWER)
            .build()
        )
      )
    }

    private fun canRunJob(): Boolean {
      return if (!RemoteConfig.keyTransparency) {
        Log.i(TAG, "Remote config is not on. Exiting.")
        false
      } else if (!SignalStore.account.isRegistered) {
        Log.i(TAG, "Account not registered. Exiting.")
        false
      } else if (!SignalStore.settings.automaticVerificationEnabled) {
        Log.i(TAG, "Automatic verification disabled. Exiting.")
        false
      } else if (SignalStore.account.usernameSyncState != AccountValues.UsernameSyncState.IN_SYNC) {
        Log.i(TAG, "Username is in a bad state. Exiting.")
        false
      } else if (!Recipient.self().hasAci || !Recipient.self().hasE164) {
        Log.i(TAG, "Missing an ACI or E164. Exiting.")
        false
      } else {
        true
      }
    }
  }

  override suspend fun doRun(): Result {
    if (!canRunJob()) {
      return Result.failure()
    }

    SignalStore.misc.lastKeyTransparencyTime = System.currentTimeMillis()

    val recipient = SignalDatabase.recipients.getRecord(Recipient.self().id)
    val aciIdentityKey = SignalStore.account.aciIdentityKey.publicKey
    val aci = recipient.aci!!.libSignalAci

    val (e164, unidentifiedAccessKey) = if (SignalStore.phoneNumberPrivacy.phoneNumberDiscoverabilityMode == PhoneNumberDiscoverabilityMode.DISCOVERABLE) {
      Pair(recipient.e164!!, ProfileKeyUtil.profileKeyOrNull(recipient.profileKey).let { UnidentifiedAccess.deriveAccessKeyFrom(it) })
    } else {
      Pair(null, null)
    }

    val usernameHash = SignalStore.account.username?.let { Username(it).hash }
    val firstSearch = recipient.keyTransparencyData == null

    val result = if (firstSearch) {
      Log.i(TAG, "First search in key transparency")
      SignalNetwork.keyTransparency.search(aci, aciIdentityKey, e164, unidentifiedAccessKey, usernameHash, KeyTransparencyStore)
    } else {
      Log.i(TAG, "Monitoring search in key transparency")
      SignalNetwork.keyTransparency.monitor(KeyTransparency.MonitorMode.SELF, aci, aciIdentityKey, e164, unidentifiedAccessKey, usernameHash, KeyTransparencyStore)
    }

    Log.i(TAG, "Key transparency complete, result: $result")
    return when (result) {
      is RequestResult.Success -> {
        SignalStore.misc.hasKeyTransparencyFailure = false
        SignalStore.misc.hasSeenKeyTransparencyFailure = false
        Result.success()
      }

      is RequestResult.NonSuccess -> {
        if (result.error.exception is IllegalArgumentException) {
          Log.w(TAG, "KT store was corrupted. Restarting and then retrying.")
          SignalStore.account.distinguishedHead = null
          SignalDatabase.recipients.clearSelfKeyTransparencyData()
          Result.retry(defaultBackoff())
        } else if (result.error.exception is VerificationFailedException || result.error.exception is KeyTransparencyException) {
          if (!showFailure) {
            Log.w(TAG, "Verification failure. Enqueuing this job again to run again a day.")
            StorageSyncJob.forRemoteChange()
            enqueueFollowingFailure()
          } else {
            Log.w(TAG, "Second verification failure. Showing failure sheet.")
            markFailure()
          }
          Result.failure()
        } else if (result.error.exception is AppExpiredException) {
          Result.failure()
        } else {
          Log.w(TAG, "Unknown nonsuccess failure. Showing failure sheet.")
          markFailure()
          Result.failure()
        }
      }
      is RequestResult.RetryableNetworkError -> {
        if (result.retryAfter != null) {
          Result.retry(result.retryAfter!!.toMillis())
        } else {
          Result.retry(defaultBackoff())
        }
      }
      is RequestResult.ApplicationError -> {
        Log.w(TAG, "Unknown application failure. Showing failure sheet.")
        markFailure()
        Result.failure()
      }
    }
  }

  /**
   * Flags a failure in key transparency. For internal users, always force it to be shown.
   * For others, it will only show once and only be cleared on the next successful verification.
   */
  private fun markFailure() {
    SignalStore.misc.hasKeyTransparencyFailure = true
    if (RemoteConfig.internalUser) {
      SignalStore.misc.hasSeenKeyTransparencyFailure = false
    }
  }

  override fun serialize(): ByteArray {
    return CheckKeyTransparencyJobData(showFailure).encode()
  }

  override fun getFactoryKey(): String = KEY

  override fun onFailure() = Unit

  class Factory : Job.Factory<CheckKeyTransparencyJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): CheckKeyTransparencyJob {
      val jobData = CheckKeyTransparencyJobData.ADAPTER.decode(serializedData!!)
      return CheckKeyTransparencyJob(jobData.showFailure, parameters)
    }
  }
}
