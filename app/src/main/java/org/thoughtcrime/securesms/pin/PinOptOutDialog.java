package org.thoughtcrime.securesms.pin;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.signal.core.util.concurrent.SimpleTask;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.views.SimpleProgressDialog;

public final class PinOptOutDialog {

  private static final String TAG = Log.tag(PinOptOutDialog.class);

  /**
   * @param rotateAep If true, this will rotate the AEP as part of the process of opting out. Only do this if the user has not enabled backups! If the user
   *    has backups enabled, you should guide them through rotating the AEP first, and then call this with [rotateAep] = false.
   */
  public static void show(@NonNull Context context, boolean rotateAep, @NonNull Runnable onSuccess) {
    Log.i(TAG, "show()");
    AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                                        .setTitle(R.string.PinOptOutDialog_warning)
                                        .setMessage(R.string.PinOptOutDialog_if_you_disable_the_pin_you_will_lose_all_data)
                                        .setCancelable(true)
                                        .setPositiveButton(R.string.PinOptOutDialog_disable_pin, (d, which) -> {
                                          Log.i(TAG, "Disable clicked.");
                                          d.dismiss();
                                          AlertDialog progress = SimpleProgressDialog.show(context);

                                          SimpleTask.run(() -> {
                                            SvrRepository.optOutOfPin(rotateAep);
                                            return null;
                                          }, success -> {
                                            Log.i(TAG, "Disable operation finished.");
                                            onSuccess.run();
                                            progress.dismiss();
                                          });
                                         })
                                        .setNegativeButton(android.R.string.cancel, (d, which) -> {
                                          Log.i(TAG, "Cancel clicked.");
                                          d.dismiss();
                                        })
                                        .create();

    dialog.setOnShowListener(dialogInterface -> {
      dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ContextCompat.getColor(context, R.color.signal_alert_primary));
    });

    dialog.show();
  }
}
