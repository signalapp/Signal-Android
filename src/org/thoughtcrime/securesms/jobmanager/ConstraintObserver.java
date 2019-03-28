package org.thoughtcrime.securesms.jobmanager;

import android.support.annotation.NonNull;

public interface ConstraintObserver {

  void register(@NonNull Notifier notifier);

  interface Notifier {
    void onConstraintMet(@NonNull String reason);
  }
}
