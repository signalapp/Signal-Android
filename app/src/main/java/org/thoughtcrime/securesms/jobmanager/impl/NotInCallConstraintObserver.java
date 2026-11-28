package org.thoughtcrime.securesms.jobmanager.impl;

import androidx.annotation.NonNull;

import org.signal.core.util.eventbus.EventBus;
import org.signal.core.util.eventbus.ThreadMode;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.events.WebRtcViewModel;
import org.thoughtcrime.securesms.jobmanager.ConstraintObserver;

public final class NotInCallConstraintObserver implements ConstraintObserver {

  private static final String REASON = Log.tag(NotInCallConstraintObserver.class);

  @Override
  public void register(@NonNull Notifier notifier) {
    EventBus.getDefault().subscribeSticky(WebRtcViewModel.class, ThreadMode.MAIN, viewModel -> {
      NotInCallConstraint constraint = new NotInCallConstraint.Factory().create();

      if (constraint.isMet()) {
        notifier.onConstraintMet(REASON);
      }
    });
  }
}
