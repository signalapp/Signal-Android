package org.thoughtcrime.securesms.jobmanager.impl;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.ConstraintObserver;

/**
 * An observer for {@link WebsocketDrainedConstraint}. Will fire when the
 * {@link org.thoughtcrime.securesms.messages.InitialMessageRetriever} is caught up.
 */
public class WebsocketDrainedConstraintObserver implements ConstraintObserver {

  private static final String REASON = WebsocketDrainedConstraintObserver.class.getSimpleName();

  @Override
  public void register(@NonNull Notifier notifier) {
    ApplicationDependencies.getInitialMessageRetriever().addListener(() -> {
      notifier.onConstraintMet(REASON);
    });
  }
}
