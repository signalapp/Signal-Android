package org.thoughtcrime.securesms.sharing;

import android.app.AlertDialog;
import android.content.Context;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.R;

public final class MultiShareDialogs {
  private MultiShareDialogs() {
  }

  public static void displayResultDialog(@NonNull Context context,
                                         @NonNull MultiShareSender.MultiShareSendResultCollection resultCollection,
                                         @NonNull Runnable onDismiss)
  {
    if (resultCollection.containsFailures()) {
      displayFailuresDialog(context, onDismiss);
    } else {
      onDismiss.run();
    }
  }

  public static void displayMaxSelectedDialog(@NonNull Context context, int hardLimit) {
    new AlertDialog.Builder(context)
                   .setMessage(context.getString(R.string.MultiShareDialogs__you_can_only_share_with_up_to, hardLimit))
                   .setPositiveButton(android.R.string.ok, ((dialog, which) -> dialog.dismiss()))
                   .setCancelable(true)
                   .show();
  }

  private static void displayFailuresDialog(@NonNull Context context,
                                            @NonNull Runnable onDismiss)
  {
    new AlertDialog.Builder(context)
                   .setMessage(R.string.MultiShareDialogs__failed_to_send_to_some_users)
                   .setPositiveButton(android.R.string.ok, ((dialog, which) -> dialog.dismiss()))
                   .setOnDismissListener(dialog -> onDismiss.run())
                   .setCancelable(true)
                   .show();
  }
}
