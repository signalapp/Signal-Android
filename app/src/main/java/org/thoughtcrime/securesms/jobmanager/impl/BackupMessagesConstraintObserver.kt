package org.thoughtcrime.securesms.jobmanager.impl

import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.ConstraintObserver

/**
 * An observer for the [BackupMessagesConstraint]. This is called when users change whether or not backup is allowed via cellular
 */
object BackupMessagesConstraintObserver : ConstraintObserver {

  private const val REASON = "BackupMessagesConstraint"

  private var notifier: ConstraintObserver.Notifier? = null

  override fun register(notifier: ConstraintObserver.Notifier) {
    this.notifier = notifier
  }

  /**
   * Let the observer know that the backup using cellular flag has changed.
   */
  fun onChange() {
    if (BackupMessagesConstraint.isMet(AppDependencies.application)) {
      notifier?.onConstraintMet(REASON)
    }
  }
}
