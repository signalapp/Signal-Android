package org.thoughtcrime.securesms.shakereport;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Lifecycle;

import org.signal.core.util.ShakeDetector;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.signal.core.util.AppForegroundObserver;
import org.signal.core.util.ServiceUtil;

import java.lang.ref.WeakReference;

/**
 * A class that will detect a shake and then prompts the user to submit a debuglog. Basically a
 * shortcut to submit a debuglog from anywhere.
 */
public final class ShakeToReport implements ShakeDetector.Listener {

  private static final String TAG = Log.tag(ShakeToReport.class);

  private final Application   application;
  private final ShakeDetector detector;

  private WeakReference<AppCompatActivity> weakActivity;

  public ShakeToReport(@NonNull Application application) {
    this.application  = application;
    this.detector     = new ShakeDetector(this);
    this.weakActivity = new WeakReference<>(null);
  }

  public void enable() {
    if (!SignalStore.internal().getShakeToReport()) return;

    detector.start(ServiceUtil.getSensorManager(application));
  }

  public void disable() {
    if (!SignalStore.internal().getShakeToReport()) return;

    detector.stop();
  }

  public void registerActivity(@NonNull AppCompatActivity activity) {
    if (!SignalStore.internal().getShakeToReport()) return;

    this.weakActivity = new WeakReference<>(activity);
  }

  @Override
  public void onShakeDetected() {
    if (!SignalStore.internal().getShakeToReport()) return;

    AppCompatActivity activity = weakActivity.get();
    if (activity == null) {
      Log.w(TAG, "No registered activity!");
      return;
    }

    if (activity.getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED)) {
      disable();

      if (!ShakeToReportDialogFragment.show(activity.getSupportFragmentManager())) {
        enableIfVisible();
      }
    }
  }

  public void enableIfVisible() {
    if (AppForegroundObserver.isForegrounded()) {
      enable();
    }
  }
}
