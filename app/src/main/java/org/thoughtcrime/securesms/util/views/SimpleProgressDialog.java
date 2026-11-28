package org.thoughtcrime.securesms.util.views;

import android.app.Activity;
import android.content.Context;

import androidx.annotation.AnyThread;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

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
    AlertDialog dialog = new MaterialAlertDialogBuilder(context)
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
      if (!canShowDialog(context)) {
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
          if (dialogAtomicReference.get() == null) {
            return;
          }

          long beenShowingForMs = System.currentTimeMillis() - shownAt.get();
          long remainingTimeMs  = minimumShowTimeMs - beenShowingForMs;

          if (remainingTimeMs > 0) {
            ThreadUtil.runOnMainDelayed(() -> dismissIfNeeded(context, dialogAtomicReference), remainingTimeMs);
          } else {
            dismissIfNeeded(context, dialogAtomicReference);
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

  @MainThread
  private static void dismissIfNeeded(@NonNull Context context, @NonNull AtomicReference<AlertDialog> dialogAtomicReference) {
    if (dialogAtomicReference.get() == null) {
      return;
    }

    if (!canDismissDialog(context)) {
      Log.w(TAG, "Context is no longer valid. Not dismissing dialog.");
      return;
    }

    AlertDialog alertDialog = dialogAtomicReference.getAndSet(null);
    if (alertDialog != null) {
      alertDialog.dismiss();
    }
  }

  /** A window can only be added once the activity is at least started, so showing requires that; dismissing does not. */
  private static boolean canShowDialog(@NonNull Context context) {
    if (context instanceof LifecycleOwner) {
      return isActivityAlive(context) && ((LifecycleOwner) context).getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED);
    } else {
      return isActivityAlive(context);
    }
  }

  private static boolean canDismissDialog(@NonNull Context context) {
    return isActivityAlive(context);
  }

  private static boolean isActivityAlive(@NonNull Context context) {
    if (context instanceof Activity) {
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
