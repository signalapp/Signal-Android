package org.thoughtcrime.securesms.payments.preferences;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.thoughtcrime.securesms.R;

/**
 * Dialog to display if chosen Recipient has not enabled payments.
 */
public final class RecipientHasNotEnabledPaymentsDialog {

  private RecipientHasNotEnabledPaymentsDialog() {
  }

  public static void show(@NonNull Context context) {
    show(context, null);
  }

  public static void show(@NonNull Context context, @Nullable Runnable onDismissed) {
    new MaterialAlertDialogBuilder(context).setTitle(R.string.ConfirmPaymentFragment__invalid_recipient)
                                           .setMessage(R.string.ConfirmPaymentFragment__this_person_has_not_activated_payments)
                                           .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                                             dialog.dismiss();
                                             if (onDismissed != null) {
                                               onDismissed.run();
                                             }
                                           })
                                           .setCancelable(false)
                                           .show();
  }
}
