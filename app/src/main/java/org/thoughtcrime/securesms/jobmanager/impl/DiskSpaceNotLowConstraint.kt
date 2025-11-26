/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobmanager.impl

import android.app.job.JobInfo
import android.os.Build
import org.signal.core.util.DiskUtil
import org.signal.core.util.mebiBytes
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Constraint
import kotlin.time.Duration.Companion.seconds

/**
 * A constraint that is met so long as there is [MINIMUM_DISK_SPACE_BYTES] of space remaining on the user's disk.
 */
object DiskSpaceNotLowConstraint : Constraint {

  const val KEY = "DiskSpaceAvailableConstraint"

  private val MINIMUM_DISK_SPACE_BYTES = 100.mebiBytes.inWholeBytes
  private val CHECK_INTERVAL = 5.seconds.inWholeMilliseconds

  private var lastKnownBytesRemaining: Long = Long.MAX_VALUE
  private var lastCheckTime: Long = 0

  override fun isMet(): Boolean {
    if (System.currentTimeMillis() - lastCheckTime >= CHECK_INTERVAL) {
      lastKnownBytesRemaining = DiskUtil.getAvailableSpace(AppDependencies.application).inWholeBytes
      lastCheckTime = System.currentTimeMillis()
    }

    return lastKnownBytesRemaining > MINIMUM_DISK_SPACE_BYTES
  }

  override fun getFactoryKey(): String = KEY

  override fun applyToJobInfo(jobInfoBuilder: JobInfo.Builder) {
    if (Build.VERSION.SDK_INT >= 26) {
      jobInfoBuilder.setRequiresStorageNotLow(true)
    }
  }

  class Factory() : Constraint.Factory<DiskSpaceNotLowConstraint> {
    override fun create(): DiskSpaceNotLowConstraint {
      return DiskSpaceNotLowConstraint
    }
  }
}
