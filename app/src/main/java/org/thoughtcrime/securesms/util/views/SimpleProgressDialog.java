package org.thoughtcrime.securesms.util.views;

import android.app.Activity;
import android.content.Context;

import androidx.annotation.AnyThread;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Lifecycle;

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
  public static @NonNull DismissibleDialog showDelayed(@NonNull Context context) {
    return showDelayed(context, 300, 1000);
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
                                                       int delayMs,
                                                       int minimumShowTimeMs)
  {
    AtomicReference<AlertDialog> dialogAtomicReference = new AtomicReference<>();
    AtomicLong                   shownAt               = new AtomicLong();

    Runnable showRunnable = () -> {
      if (!isContextValid(context)) {
        Log.w(TAG, "Context is no longer valid. Not showing dialog.");
        return;
      }

      Log.i(TAG, "Taking some time. Showing a progress dialog.");
      shownAt.set(System.currentTimeMillis());
      dialogAtomicReference.set(show(context));
    };

    ThreadUtil.runOnMainDelayed(showRunnable, delayMs);

    return new DismissibleDialog() {
      @Override
      public void dismiss() {
        ThreadUtil.cancelRunnableOnMain(showRunnable);
        ThreadUtil.runOnMain(() -> {
          if (!isContextValid(context)) {
            Log.w(TAG, "Context is no longer valid. Not dismissing dialog.");
            return;
          }

          AlertDialog alertDialog = dialogAtomicReference.getAndSet(null);
          if (alertDialog != null) {
            long beenShowingForMs = System.currentTimeMillis() - shownAt.get();
            long remainingTimeMs  = minimumShowTimeMs - beenShowingForMs;

            if (remainingTimeMs > 0) {
              ThreadUtil.runOnMainDelayed(() -> {
                if (!isContextValid(context)) {
                  Log.w(TAG, "Context is no longer valid. Not dismissing dialog.");
                  return;
                }

                alertDialog.dismiss();
              }, remainingTimeMs);
            } else {
              alertDialog.dismiss();
            }
          }
        });
      }

      @Override
      public void dismissNow() {
        ThreadUtil.cancelRunnableOnMain(showRunnable);
        ThreadUtil.runOnMain(() -> {
          AlertDialog alertDialog = dialogAtomicReference.getAndSet(null);
          if (alertDialog != null) {
            alertDialog.dismiss();
          }
        });
      }
    };
  }

  private static boolean isContextValid(@NonNull Context context) {
    if (context instanceof AppCompatActivity) {
      AppCompatActivity activity = (AppCompatActivity) context;
      return !activity.isFinishing() && !activity.isDestroyed() && activity.getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED);
    } else if (context instanceof Activity) {
      Activity activity = (Activity) context;
      return !activity.isFinishing() && !activity.isDestroyed();
    } else {
      return true;
    }
  }

  public interface DismissibleDialog {
    @AnyThread
    void dismiss();

    @AnyThread
    void dismissNow();
  }
}
