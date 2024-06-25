package org.thoughtcrime.securesms.jobs

import org.signal.core.util.Base64
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.ProfileUtil
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile
import java.io.IOException
import kotlin.time.Duration.Companion.days

/**
 * The worker job for [org.thoughtcrime.securesms.migrations.AccountConsistencyMigrationJob].
 */
class AccountConsistencyWorkerJob private constructor(parameters: Parameters) : BaseJob(parameters) {

  companion object {
    private val TAG = Log.tag(AccountConsistencyWorkerJob::class.java)

    const val KEY = "AccountConsistencyWorkerJob"

    @JvmStatic
    fun enqueueIfNecessary() {
      if (System.currentTimeMillis() - SignalStore.misc.lastConsistencyCheckTime > 3.days.inWholeMilliseconds) {
        AppDependencies.jobManager.add(AccountConsistencyWorkerJob())
      }
    }
  }

  constructor() : this(
    Parameters.Builder()
      .setMaxInstancesForFactory(1)
      .addConstraint(NetworkConstraint.KEY)
      .setMaxAttempts(Parameters.UNLIMITED)
      .setLifespan(30.days.inWholeMilliseconds)
      .build()
  )

  override fun serialize(): ByteArray? = null

  override fun getFactoryKey(): String = KEY

  override fun onFailure() = Unit

  override fun onRun() {
    if (!SignalStore.account.hasAciIdentityKey()) {
      Log.i(TAG, "No identity set yet, skipping.")
      return
    }

    if (!SignalStore.account.isRegistered || SignalStore.account.aci == null) {
      Log.i(TAG, "Not yet registered, skipping.")
      return
    }

    val aciProfile: SignalServiceProfile = ProfileUtil.retrieveProfileSync(context, Recipient.self(), SignalServiceProfile.RequestType.PROFILE, false).profile
    val encodedAciPublicKey = Base64.encodeWithPadding(SignalStore.account.aciIdentityKey.publicKey.serialize())

    if (aciProfile.identityKey != encodedAciPublicKey) {
      Log.w(TAG, "ACI identity key on profile differed from the one we have locally! Marking ourselves unregistered.")

      SignalStore.account.setRegistered(false)
      SignalStore.registration.clearRegistrationComplete()
      SignalStore.registration.clearHasUploadedProfile()

      SignalStore.misc.lastConsistencyCheckTime = System.currentTimeMillis()
      return
    }

    val pniProfile: SignalServiceProfile = ProfileUtil.retrieveProfileSync(SignalStore.account.pni!!, SignalServiceProfile.RequestType.PROFILE).profile
    val encodedPniPublicKey = Base64.encodeWithPadding(SignalStore.account.pniIdentityKey.publicKey.serialize())

    if (pniProfile.identityKey != encodedPniPublicKey) {
      Log.w(TAG, "PNI identity key on profile differed from the one we have locally!")

      SignalStore.account.setRegistered(false)
      SignalStore.registration.clearRegistrationComplete()
      SignalStore.registration.clearHasUploadedProfile()
      return
    }

    Log.i(TAG, "Everything matched.")

    SignalStore.misc.lastConsistencyCheckTime = System.currentTimeMillis()
  }

  override fun onShouldRetry(e: Exception): Boolean {
    return e is IOException
  }

  class Factory : Job.Factory<AccountConsistencyWorkerJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): AccountConsistencyWorkerJob {
      return AccountConsistencyWorkerJob(parameters)
    }
  }
}
