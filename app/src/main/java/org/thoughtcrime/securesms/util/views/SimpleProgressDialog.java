package org.thoughtcrime.securesms.util.views;

import android.content.Context;

import androidx.annotation.AnyThread;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import org.signal.core.util.ThreadUtil;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @deprecated  Replaced by {@link org.thoughtcrime.securesms.components.SignalProgressDialog}
 */
@Deprecated
public final class SimpleProgressDialog {

  private static final String TAG = Log.tag(SimpleProgressDialog.class);

  private SimpleProgressDialog() {}

  @MainThread
  public static @NonNull AlertDialog show(@NonNull Context context) {
    AlertDialog dialog = new AlertDialog.Builder(context)
                                        .setView(R.layout.progress_dialog)
                                        .setCancelable(false)
                                        .create();
    dialog.show();
    dialog.getWindow().setLayout(context.getResources().getDimensionPixelSize(R.dimen.progress_dialog_size),
                                 context.getResources().getDimensionPixelSize(R.dimen.progress_dialog_size));

    return dialog;
  }

  @AnyThread
  public static @NonNull DismissibleDialog showDelayed(@NonNull Context context,
                                                       @NonNull LifecycleOwner lifecycleOwner)
  {
    return showDelayed(context, lifecycleOwner, 300, 1000);
  }

  /**
   * Shows the dialog after {@param delayMs} ms.
   * <p>
   * To dismiss, call {@link DismissibleDialog#dismiss()} on the result. If dismiss is called before
   * the delay has elapsed, the dialog will not show at all.
   * <p>
   * Dismiss can be called on any thread.
   *
   * @param minimumShowTimeMs If the dialog does display, then it will be visible for at least this duration.
   *                          This is to prevent flicker.
   */
  @AnyThread
  public static @NonNull DismissibleDialog showDelayed(@NonNull Context context,
                                                       @NonNull LifecycleOwner lifecycleOwner,
                                                       int delayMs,
                                                       int minimumShowTimeMs)
  {
    AtomicReference<AlertDialog> dialogAtomicReference = new AtomicReference<>();
    AtomicLong                   shownAt               = new AtomicLong();

    final AtomicReference<Runnable> showRunnable = new AtomicReference<>(null);

    final Runnable tryDismiss = () -> {
      AlertDialog alertDialog = dialogAtomicReference.getAndSet(null);
      if (alertDialog != null) {
        alertDialog.dismiss();
      }
    };

    DismissibleDialog dialog = new DismissibleDialog() {
      @Override
      public void dismiss() {
        ThreadUtil.cancelRunnableOnMain(showRunnable.get());

        long beenShowingForMs = System.currentTimeMillis() - shownAt.get();
        long remainingTimeMs  = minimumShowTimeMs + 1000 - beenShowingForMs;
        long timeUntilDismiss = Math.max(remainingTimeMs, 0);

        ThreadUtil.runOnMainDelayed(tryDismiss, timeUntilDismiss);
      }

      @Override
      public void dismissNow() {
        ThreadUtil.cancelRunnableOnMain(showRunnable.get());
        ThreadUtil.runOnMain(tryDismiss);
      }
    };

    showRunnable.set(() -> {
      Log.i(TAG, "Taking some time. Showing a progress dialog.");
      shownAt.set(System.currentTimeMillis());
      dialogAtomicReference.set(show(context));

      LifecycleObserver observer = new DefaultLifecycleObserver() {
        @Override public void onStop(@NonNull LifecycleOwner owner) {
          // avoid leaking the dialog when its parent activity is destroyed
          tryDismiss.run();
        }
      };
      lifecycleOwner.getLifecycle().addObserver(observer);
    });

    ThreadUtil.runOnMainDelayed(showRunnable.get(), delayMs);

    return dialog;
  };

  public interface DismissibleDialog {
    @AnyThread
    void dismiss();

    @AnyThread
    void dismissNow();
  }
}
