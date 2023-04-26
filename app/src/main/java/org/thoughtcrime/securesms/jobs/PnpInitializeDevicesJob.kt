package org.thoughtcrime.securesms.jobs

import org.signal.core.util.concurrent.safeBlockingGet
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.components.settings.app.changenumber.ChangeNumberRepository
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.registration.VerifyResponseWithoutKbs
import org.thoughtcrime.securesms.util.FeatureFlags
import org.thoughtcrime.securesms.util.TextSecurePreferences
import java.io.IOException

/**
 * To be run when all clients support PNP and we need to initialize all linked devices with appropriate PNP data.
 *
 * We reuse the change number flow as it already support distributing the necessary data in a way linked devices can understand.
 */
class PnpInitializeDevicesJob private constructor(parameters: Parameters) : BaseJob(parameters) {

  companion object {
    const val KEY = "PnpInitializeDevicesJob"
    private val TAG = Log.tag(PnpInitializeDevicesJob::class.java)
    private const val PLACEHOLDER_SESSION_ID = "123456789"

    @JvmStatic
    fun enqueueIfNecessary() {
      if (SignalStore.misc().hasPniInitializedDevices() || !SignalStore.account().isRegistered || SignalStore.account().aci == null || Recipient.self().pnpCapability != Recipient.Capability.SUPPORTED || !FeatureFlags.phoneNumberPrivacy()) {
        return
      }

      ApplicationDependencies.getJobManager().add(PnpInitializeDevicesJob())
    }
  }

  constructor() : this(Parameters.Builder().addConstraint(NetworkConstraint.KEY).build())

  override fun serialize(): ByteArray? {
    return null
  }

  override fun getFactoryKey(): String {
    return KEY
  }

  override fun onFailure() = Unit

  @Throws(Exception::class)
  public override fun onRun() {
    if (Recipient.self().pnpCapability != Recipient.Capability.SUPPORTED) {
      throw IllegalStateException("This should only be run if you have the capability!")
    }

    if (!FeatureFlags.phoneNumberPrivacy()) {
      throw IllegalStateException("This should only be running if PNP is enabled!")
    }

    if (!SignalStore.account().isRegistered || SignalStore.account().aci == null) {
      Log.w(TAG, "Not registered! Skipping, as it wouldn't do anything.")
      return
    }

    if (!TextSecurePreferences.isMultiDevice(context)) {
      Log.i(TAG, "Not multi device, aborting...")
      SignalStore.misc().setPniInitializedDevices(true)
      return
    }

    if (SignalStore.account().isLinkedDevice) {
      Log.i(TAG, "Not primary device, aborting...")
      SignalStore.misc().setPniInitializedDevices(true)
      return
    }

    ChangeNumberRepository.CHANGE_NUMBER_LOCK.lock()
    try {
      if (SignalStore.misc().hasPniInitializedDevices()) {
        Log.w(TAG, "We found out that things have been initialized after we got the lock! No need to do anything else.")
        return
      }

      val changeNumberRepository = ChangeNumberRepository()
      val e164 = SignalStore.account().requireE164()

      try {
        Log.i(TAG, "Calling change number with our current number to distribute PNI messages")
        changeNumberRepository
          .changeNumber(sessionId = PLACEHOLDER_SESSION_ID, newE164 = e164, pniUpdateMode = true)
          .map(::VerifyResponseWithoutKbs)
          .safeBlockingGet()
          .resultOrThrow
      } catch (e: InterruptedException) {
        throw IOException("Retry", e)
      } catch (t: Throwable) {
        Log.w(TAG, "Unable to initialize PNI for linked devices", t)
        throw t
      }

      SignalStore.misc().setPniInitializedDevices(true)
    } finally {
      ChangeNumberRepository.CHANGE_NUMBER_LOCK.unlock()
    }
  }

  override fun onShouldRetry(e: Exception): Boolean {
    return e is IOException
  }

  class Factory : Job.Factory<PnpInitializeDevicesJob?> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): PnpInitializeDevicesJob {
      return PnpInitializeDevicesJob(parameters)
    }
  }
}
