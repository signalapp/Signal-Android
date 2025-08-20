/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobmanager.impl

import android.app.job.JobInfo
import org.thoughtcrime.securesms.jobmanager.Constraint
import org.thoughtcrime.securesms.jobmanager.ConstraintObserver
import org.thoughtcrime.securesms.keyvalue.SignalStore

/**
 * A constraint that is met so long as there is no remote storage garbage collection pending.
 * "Remote storage garbage collection" refers to the process of cleaning up unused or orphaned media files from the remote archive storage.
 * We won't be put into garbage collection mode unless we've received some indication from the server that we've run out of space.
 *
 * Use this constraint to prevent jobs that require remote storage from running until we've done everything we can to free up space.
 */
class NoRemoteArchiveGarbageCollectionPendingConstraint : Constraint {

  companion object {
    const val KEY = "NoRemoteArchiveGarbageCollectionPendingConstraint"
  }

  override fun isMet(): Boolean {
    if (!SignalStore.backup.areBackupsEnabled) {
      return true
    }

    if (!SignalStore.backup.backsUpMedia) {
      return true
    }

    return !SignalStore.backup.remoteStorageGarbageCollectionPending
  }

  override fun getFactoryKey(): String = KEY

  override fun applyToJobInfo(jobInfoBuilder: JobInfo.Builder) = Unit

  object Observer : ConstraintObserver {
    val listeners: MutableSet<ConstraintObserver.Notifier> = mutableSetOf()

    override fun register(notifier: ConstraintObserver.Notifier) {
      listeners += notifier
    }

    fun notifyListeners() {
      for (listener in listeners) {
        listener.onConstraintMet(KEY)
      }
    }
  }

  class Factory : Constraint.Factory<NoRemoteArchiveGarbageCollectionPendingConstraint> {
    override fun create(): NoRemoteArchiveGarbageCollectionPendingConstraint {
      return NoRemoteArchiveGarbageCollectionPendingConstraint()
    }
  }
}
