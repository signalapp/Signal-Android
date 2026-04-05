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
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Constraint that holds jobs until the sealed sender certificate is confirmed valid.
 * This prevents send jobs from firing with expired certificates after the device wakes
 * from a long sleep.
 */
object SealedSenderConstraint : Constraint {

  const val KEY = "SealedSenderConstraint"

  private val TAG = Log.tag(SealedSenderConstraint::class.java)
  private val CERTIFICATE_EXPIRATION_BUFFER = TimeUnit.DAYS.toMillis(1)

  private val valid = AtomicBoolean(false)

  override fun isMet(): Boolean = valid.get()

  override fun getFactoryKey(): String = KEY

  override fun applyToJobInfo(jobInfoBuilder: JobInfo.Builder) = Unit

  @JvmStatic
  fun markValid() {
    valid.set(true)
    Observer.onChange()
  }

  /**
   * Checks all required certificate types. If all are present and not near expiry,
   * marks the constraint as valid. Otherwise enqueues a [RotateCertificateJob] and
   * leaves the constraint unmet until the rotation completes and calls [markValid].
   */
  @JvmStatic
  fun checkAndSetValidity() {
    try {
      val requiredTypes = SignalStore.phoneNumberPrivacy.getRequiredCertificateTypes()

      for (certificateType in requiredTypes) {
        val certificateBytes = SignalStore.certificate.getUnidentifiedAccessCertificate(certificateType)

        if (certificateBytes == null) {
          Log.w(TAG, "Missing certificate $certificateType. Enqueuing rotation.")
          AppDependencies.jobManager.add(RotateCertificateJob())
          return
        }

        val certificate = SenderCertificate(certificateBytes)
        if (System.currentTimeMillis() > certificate.expiration - CERTIFICATE_EXPIRATION_BUFFER) {
          Log.w(TAG, "Certificate $certificateType is expired or near expiry. Enqueuing rotation.")
          AppDependencies.jobManager.add(RotateCertificateJob())
          return
        }
      }

      Log.i(TAG, "All sealed sender certificates are valid.")
      markValid()
    } catch (e: Exception) {
      Log.w(TAG, "Error checking certificate validity. Enqueuing rotation.", e)
      AppDependencies.jobManager.add(RotateCertificateJob())
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
