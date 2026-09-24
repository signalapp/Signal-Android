package org.thoughtcrime.securesms.jobmanager.impl

import android.app.job.JobInfo
import org.signal.core.util.logging.Log
import org.signal.libsignal.metadata.certificate.SenderCertificate
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Constraint
import org.thoughtcrime.securesms.jobmanager.ConstraintObserver
import org.thoughtcrime.securesms.jobs.RotateCertificateJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import java.util.concurrent.TimeUnit

/**
 * Constraint that holds jobs until the sealed sender certificate is confirmed valid.
 * This prevents send jobs from firing with expired certificates after the device wakes
 * from a long sleep.
 */
object SealedSenderConstraint : Constraint {

  const val KEY = "SealedSenderConstraint"

  private val TAG = Log.tag(SealedSenderConstraint::class.java)
  private val ROTATION_LEAD_TIME = TimeUnit.DAYS.toMillis(1)

  @Volatile
  private var expiresAt: Long = 0

  override fun isMet(): Boolean = System.currentTimeMillis() < expiresAt

  override fun getFactoryKey(): String = KEY

  override fun applyToJobInfo(jobInfoBuilder: JobInfo.Builder) = Unit

  @JvmStatic
  fun refresh() {
    expiresAt = computeExpiresAt()

    if (isMet()) {
      Observer.onChange()
    }
  }

  @JvmStatic
  fun refreshAndRotateIfNeeded() {
    refresh()

    if (System.currentTimeMillis() > expiresAt - ROTATION_LEAD_TIME) {
      Log.w(TAG, "A sealed sender certificate is missing, expired, or nearly expired. Enqueuing rotation.")
      AppDependencies.jobManager.add(RotateCertificateJob())
    } else {
      Log.i(TAG, "All sealed sender certificates are valid.")
    }
  }

  private fun computeExpiresAt(): Long {
    return try {
      val requiredTypes = SignalStore.phoneNumberPrivacy.requiredCertificateTypes
      var earliest = Long.MAX_VALUE

      for (certificateType in requiredTypes) {
        val certificateBytes = SignalStore.certificate.getUnidentifiedAccessCertificate(certificateType) ?: return 0
        earliest = minOf(earliest, SenderCertificate(certificateBytes).expiration)
      }

      if (requiredTypes.isEmpty()) {
        Long.MAX_VALUE
      } else {
        earliest + SignalStore.misc.lastKnownServerTimeOffset
      }
    } catch (e: Exception) {
      Log.w(TAG, "Error reading certificate validity.", e)
      0
    }
  }

  object Observer : ConstraintObserver {
    private var notifier: ConstraintObserver.Notifier? = null

    override fun register(notifier: ConstraintObserver.Notifier) {
      this.notifier = notifier
    }

    fun onChange() {
      notifier?.onConstraintMet(KEY)
    }
  }

  class Factory : Constraint.Factory<SealedSenderConstraint> {
    override fun create(): SealedSenderConstraint = SealedSenderConstraint
  }
}
