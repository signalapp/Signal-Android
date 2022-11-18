package org.thoughtcrime.securesms.pin;

import android.content.Context;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.Mp02CustomDialog;
import org.thoughtcrime.securesms.util.concurrent.SimpleTask;

import java.io.IOException;
import java.util.Objects;

public final class RegistrationLockV2Dialog {

  private static final String TAG = Log.tag(RegistrationLockV2Dialog.class);

  private RegistrationLockV2Dialog() {}

  public static void showEnableDialog(@NonNull Context context, @NonNull Runnable onSuccess) {
    int titleRes = R.string.RegistrationLockV2Dialog_turn_on_registration_lock;
    int bodyRes = R.string.RegistrationLockV2Dialog_if_you_forget_your_signal_pin_when_registering_again;

    Mp02CustomDialog dialog = new Mp02CustomDialog(context);
    dialog.setMessage(context.getString(titleRes) + '\n' + context.getString(bodyRes));
    dialog.setCancelable(true);
    dialog.setPositiveListener(R.string.RegistrationLockV2Dialog_turn_on, () -> {

      SimpleTask.run(SignalExecutors.UNBOUNDED, () -> {
        try {
          PinState.onEnableRegistrationLockForUserWithPin();
          Log.i(TAG, "Successfully enabled registration lock.");
          return true;
        } catch (IOException e) {
          Log.w(TAG, "Failed to enable registration lock setting.", e);
          return false;
        }
      }, (success) -> {
        if (!success) {
          Toast.makeText(context, R.string.preferences_app_protection__failed_to_enable_registration_lock, Toast.LENGTH_LONG).show();
        } else {
          onSuccess.run();
        }

        dialog.dismiss();
      });
    });

    dialog.setNegativeListener(android.R.string.cancel, null);
    dialog.show();
  }

  public static void showDisableDialog(@NonNull Context context, @NonNull Runnable onSuccess) {
    int titleRes = R.string.RegistrationLockV2Dialog_turn_off_registration_lock;

    Mp02CustomDialog dialog = new Mp02CustomDialog(context);
    dialog.setMessage(context.getString(titleRes));
    dialog.setCancelable(true);
    dialog.setPositiveListener(R.string.RegistrationLockV2Dialog_turn_off, () -> {

      SimpleTask.run(SignalExecutors.UNBOUNDED, () -> {
        try {
          PinState.onDisableRegistrationLockForUserWithPin();
          Log.i(TAG, "Successfully disabled registration lock.");
          return true;
        } catch (IOException e) {
          Log.w(TAG, "Failed to disable registration lock.", e);
          return false;
        }
      }, (success) -> {

        if (!success) {
          Toast.makeText(context, R.string.preferences_app_protection__failed_to_disable_registration_lock, Toast.LENGTH_LONG).show();
        } else {
          onSuccess.run();
        }

        dialog.dismiss();
      });
    });

    dialog.setNegativeListener(android.R.string.cancel, null);
    dialog.show();
  }
}
