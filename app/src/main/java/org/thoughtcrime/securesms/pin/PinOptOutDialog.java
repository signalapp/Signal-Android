package org.thoughtcrime.securesms.pin;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.util.ThemeUtil;
import org.thoughtcrime.securesms.util.concurrent.SimpleTask;
import org.thoughtcrime.securesms.util.views.SimpleProgressDialog;
import org.whispersystems.signalservice.internal.contacts.crypto.UnauthenticatedResponseException;

import java.io.IOException;

public final class PinOptOutDialog {

  private static final String TAG = Log.tag(PinOptOutDialog.class);

  public static void showForSkip(@NonNull Context context, @NonNull Runnable onSuccess, @NonNull Runnable onFailed) {
    show(context,
         true,
         onSuccess,
         onFailed);
  }

  public static void showForOptOut(@NonNull Context context, @NonNull Runnable onSuccess, @NonNull Runnable onFailed) {
    show(context,
         false,
         onSuccess,
         onFailed);
  }

  private static void show(@NonNull Context context,
                           boolean skip,
                           @NonNull Runnable onSuccess,
                           @NonNull Runnable onFailed)
  {
    AlertDialog dialog = new AlertDialog.Builder(context)
                                        .setTitle(R.string.PinOptOutDialog_warning)
                                        .setMessage(R.string.PinOptOutDialog_disabling_pins_will_create_a_hidden_high_entropy_pin)
                                        .setCancelable(true)
                                        .setPositiveButton(R.string.PinOptOutDialog_disable_pin, (d, which) -> {
                                          d.dismiss();
                                          AlertDialog progress = SimpleProgressDialog.show(context);

                                          SimpleTask.run(() -> {
                                            try {
                                              if (skip) {
                                                PinState.onPinCreationSkipped(context);
                                              } else {
                                                PinState.onPinOptOut(context);
                                              }
                                              return true;
                                            } catch (IOException | UnauthenticatedResponseException e) {
                                              Log.w(TAG, e);
                                              return false;
                                            }
                                          }, success -> {
                                            if (success) {
                                              onSuccess.run();
                                            } else {
                                              onFailed.run();
                                            }
                                            progress.dismiss();
                                          });
                                         })
                                        .setNegativeButton(android.R.string.cancel, (d, which) -> d.dismiss())
                                        .create();

    dialog.setOnShowListener(dialogInterface -> {
      dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ThemeUtil.getThemedColor(context, R.attr.dangerous_button_color));
    });

    dialog.show();
  }
}
