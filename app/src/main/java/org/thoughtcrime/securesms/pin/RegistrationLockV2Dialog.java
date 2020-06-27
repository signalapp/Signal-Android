package org.thoughtcrime.securesms.pin;

import android.content.Context;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.util.concurrent.SignalExecutors;
import org.thoughtcrime.securesms.util.concurrent.SimpleTask;

import java.io.IOException;
import java.util.Objects;

public final class RegistrationLockV2Dialog {

  private static final String TAG = Log.tag(RegistrationLockV2Dialog.class);

  private RegistrationLockV2Dialog() {}

  public static void showEnableDialog(@NonNull Context context, @NonNull Runnable onSuccess) {
      AlertDialog dialog = new AlertDialog.Builder(context)
                                          .setTitle(R.string.RegistrationLockV2Dialog_turn_on_registration_lock)
                                          .setView(R.layout.registration_lock_v2_dialog)
                                          .setMessage(R.string.RegistrationLockV2Dialog_if_you_forget_your_signal_pin_when_registering_again)
                                          .setNegativeButton(android.R.string.cancel, null)
                                          .setPositiveButton(R.string.RegistrationLockV2Dialog_turn_on, null)
                                          .create();
      dialog.setOnShowListener(d -> {
        ProgressBar progress       = Objects.requireNonNull(dialog.findViewById(R.id.reglockv2_dialog_progress));
        View        positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);

        positiveButton.setOnClickListener(v -> {
          progress.setIndeterminate(true);
          progress.setVisibility(View.VISIBLE);

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
            progress.setVisibility(View.GONE);

            if (!success) {
              Toast.makeText(context, R.string.preferences_app_protection__failed_to_enable_registration_lock, Toast.LENGTH_LONG).show();
            } else {
              onSuccess.run();
            }

            dialog.dismiss();
          });
        });
      });

    dialog.show();
  }

  public static void showDisableDialog(@NonNull Context context, @NonNull Runnable onSuccess) {
    AlertDialog dialog = new AlertDialog.Builder(context)
                                        .setTitle(R.string.RegistrationLockV2Dialog_turn_off_registration_lock)
                                        .setView(R.layout.registration_lock_v2_dialog)
                                        .setNegativeButton(android.R.string.cancel, null)
                                        .setPositiveButton(R.string.RegistrationLockV2Dialog_turn_off, null)
                                        .create();
    dialog.setOnShowListener(d -> {
      ProgressBar progress       = Objects.requireNonNull(dialog.findViewById(R.id.reglockv2_dialog_progress));
      View        positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);

      positiveButton.setOnClickListener(v -> {
        progress.setIndeterminate(true);
        progress.setVisibility(View.VISIBLE);

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
          progress.setVisibility(View.GONE);

          if (!success) {
            Toast.makeText(context, R.string.preferences_app_protection__failed_to_disable_registration_lock, Toast.LENGTH_LONG).show();
          } else {
            onSuccess.run();
          }

          dialog.dismiss();
        });
      });
    });

    dialog.show();
  }
}
