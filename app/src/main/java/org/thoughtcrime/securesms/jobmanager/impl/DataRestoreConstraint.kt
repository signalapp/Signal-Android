package org.thoughtcrime.securesms.jobmanager.impl

import android.app.job.JobInfo
import org.thoughtcrime.securesms.jobmanager.Constraint

/**
 * Constraint that, when added, means that a job cannot be performed while a backup restore or device transfer
 * is occurring.
 */
object DataRestoreConstraint : Constraint {

  const val KEY = "DataRestoreConstraint"

  @JvmStatic
  var isRestoringData: Boolean = false
    set(value) {
      field = value
      DataRestoreConstraintObserver.onChange()
    }

  override fun isMet(): Boolean {
    return !isRestoringData
  }

  override fun getFactoryKey(): String = KEY

  override fun applyToJobInfo(jobInfoBuilder: JobInfo.Builder) = Unit

  class Factory : Constraint.Factory<DataRestoreConstraint> {
    override fun create(): DataRestoreConstraint {
      return DataRestoreConstraint
    }
  }
}
