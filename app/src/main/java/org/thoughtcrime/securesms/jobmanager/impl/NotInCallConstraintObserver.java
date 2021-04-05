package org.thoughtcrime.securesms.jobmanager.impl;

import androidx.annotation.NonNull;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.thoughtcrime.securesms.events.WebRtcViewModel;
import org.thoughtcrime.securesms.jobmanager.ConstraintObserver;

public final class NotInCallConstraintObserver implements ConstraintObserver {

  private static final String REASON = NotInCallConstraintObserver.class.getSimpleName();

  @Override
  public void register(@NonNull Notifier notifier) {
    EventBus.getDefault().register(new EventBusListener(notifier));
  }

  private static final class EventBusListener {

    private final Notifier notifier;

    private EventBusListener(@NonNull Notifier notifier) {
      this.notifier    = notifier;
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void consume(@NonNull WebRtcViewModel viewModel) {
      NotInCallConstraint constraint = new NotInCallConstraint.Factory().create();

      if (constraint.isMet()) {
        notifier.onConstraintMet(REASON);
      }
    }
  }
}
