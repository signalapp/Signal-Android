package org.thoughtcrime.securesms.jobmanager.impl

import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.ConstraintObserver

/**
 * An observer for the [RestoreAttachmentConstraint]. This is called
 * when users change whether or not restoring is allowed via cellular
 */
object RestoreAttachmentConstraintObserver : ConstraintObserver {

  private const val REASON = "RestoreAttachmentConstraint"

  private var notifier: ConstraintObserver.Notifier? = null

  override fun register(notifier: ConstraintObserver.Notifier) {
    this.notifier = notifier
  }

  /**
   * Let the observer know that the restore using cellular flag has changed.
   */
  fun onChange() {
    if (RestoreAttachmentConstraint.isMet(AppDependencies.application)) {
      notifier?.onConstraintMet(REASON)
    }
  }
}
