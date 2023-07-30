package org.thoughtcrime.securesms.payments;

import android.app.AlertDialog;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.thoughtcrime.securesms.R;

/**
 * Utility to display a dialog when the user tries to send a payment to someone they do not have
 * a profile key for.
 */
public final class CanNotSendPaymentDialog {

  private CanNotSendPaymentDialog() {
  }

  public static void show(@NonNull Context context) {
    show(context, null);
  }

  public static void show(@NonNull Context context, @Nullable Runnable onSendAMessageClicked) {
    MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
                                                 .setTitle(R.string.CanNotSendPaymentDialog__cant_send_payment)
                                                 .setMessage(R.string.CanNotSendPaymentDialog__to_send_a_payment_to_this_user);

    if (onSendAMessageClicked != null) {
      builder.setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.dismiss())
             .setPositiveButton(R.string.CanNotSendPaymentDialog__send_a_message, (dialog, which) -> {
               dialog.dismiss();
               onSendAMessageClicked.run();
             })
             .show();
    } else {
      builder.setPositiveButton(android.R.string.ok, (dialog, which) -> dialog.dismiss())
             .show();
    }
  }
}
