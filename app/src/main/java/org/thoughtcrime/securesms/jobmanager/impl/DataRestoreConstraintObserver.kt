package org.thoughtcrime.securesms.jobmanager.impl

import org.thoughtcrime.securesms.jobmanager.ConstraintObserver

/**
 * An observer for the [DataRestoreConstraint]. This class expects to be told when a change happens,
 * since the points at which it happens are triggered by application code.
 */
object DataRestoreConstraintObserver : ConstraintObserver {

  private const val REASON = "DataRestoreConstraint"

  private var notifier: ConstraintObserver.Notifier? = null

  override fun register(notifier: ConstraintObserver.Notifier) {
    this.notifier = notifier
  }

  /**
   * Let the observer know that the change number state has changed.
   */
  fun onChange() {
    if (DataRestoreConstraint.isMet) {
      notifier?.onConstraintMet(REASON)
    }
  }
}
