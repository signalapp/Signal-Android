package org.thoughtcrime.securesms.jobmanager.impl

import android.app.job.JobInfo
import androidx.annotation.RequiresApi
import org.thoughtcrime.securesms.jobmanager.Constraint

/**
 * Job constraint for determining whether or not the device battery is not low.
 */
class BatteryNotLowConstraint private constructor() : Constraint {
  companion object {
    const val KEY: String = "BatteryNotLowConstraint"

    fun isMet(): Boolean {
      return ChargingAndBatteryIsNotLowConstraintObserver.isCharging() || ChargingAndBatteryIsNotLowConstraintObserver.isBatteryNotLow()
    }
  }

  override fun getFactoryKey(): String = KEY

  override fun isMet(): Boolean {
    return Companion.isMet()
  }

  @RequiresApi(26)
  override fun applyToJobInfo(jobInfoBuilder: JobInfo.Builder) {
    jobInfoBuilder.setRequiresBatteryNotLow(true)
  }

  override fun getJobSchedulerKeyPart(): String? {
    return "BATTERY_NOT_LOW"
  }

  class Factory : Constraint.Factory<BatteryNotLowConstraint?> {
    override fun create(): BatteryNotLowConstraint {
      return BatteryNotLowConstraint()
    }
  }
}
