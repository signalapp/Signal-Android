/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobmanager.impl

import android.app.Application
import android.app.job.JobInfo
import android.content.Context
import org.thoughtcrime.securesms.jobmanager.Constraint
import org.thoughtcrime.securesms.keyvalue.SignalStore

/**
 * Constraint that, when added, means that a job cannot be performed unless the user either has Wifi or, if they enabled it, cellular
 */
class BackupMessagesConstraint(private val application: Application) : Constraint {

  companion object {
    const val KEY = "BackupMessagesConstraint"

    fun isMet(context: Context): Boolean {
      if (SignalStore.backup.backupWithCellular) {
        return NetworkConstraint.isMet(context)
      }
      return WifiConstraint.isMet(context)
    }
  }

  override fun isMet(): Boolean {
    return isMet(application)
  }

  override fun getFactoryKey(): String = KEY

  override fun applyToJobInfo(jobInfoBuilder: JobInfo.Builder) = Unit

  class Factory(val application: Application) : Constraint.Factory<BackupMessagesConstraint> {
    override fun create(): BackupMessagesConstraint {
      return BackupMessagesConstraint(application)
    }
  }
}
