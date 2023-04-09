package org.thoughtcrime.securesms.jobmanager.impl

import android.app.job.JobInfo
import org.thoughtcrime.securesms.jobmanager.Constraint
import org.thoughtcrime.securesms.keyvalue.SignalStore

/**
 * Constraint that, when added, means that a job cannot be performed while a change number operation is in progress.
 */
object ChangeNumberConstraint : Constraint {

  const val KEY = "ChangeNumberConstraint"

  override fun isMet(): Boolean {
    return !SignalStore.misc().isChangeNumberLocked
  }

  override fun getFactoryKey(): String = KEY

  override fun applyToJobInfo(jobInfoBuilder: JobInfo.Builder) = Unit

  class Factory : Constraint.Factory<ChangeNumberConstraint> {
    override fun create(): ChangeNumberConstraint {
      return ChangeNumberConstraint
    }
  }
}
