package org.thoughtcrime.securesms;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.concurrent.TimeUnit;

public class MuteDialog extends AlertDialog {


  protected MuteDialog(Context context) {
    super(context);
  }

  protected MuteDialog(Context context, boolean cancelable, OnCancelListener cancelListener) {
    super(context, cancelable, cancelListener);
  }

  protected MuteDialog(Context context, int theme) {
    super(context, theme);
  }

  public static void show(final Context context, final @NonNull MuteSelectionListener listener) {
    show(context, listener, null);
  }

  public static void show(final Context context, final @NonNull MuteSelectionListener listener, @Nullable Runnable cancelListener) {
    AlertDialog.Builder builder = new MaterialAlertDialogBuilder(context);
    builder.setTitle(R.string.MuteDialog_mute_notifications);
    builder.setItems(R.array.mute_durations, (dialog, which) -> {
      final long muteUntil;

      switch (which) {
        case 0:  muteUntil = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1);  break;
        case 1:  muteUntil = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(8);  break;
        case 2:  muteUntil = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1);   break;
        case 3:  muteUntil = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(7);   break;
        case 4:  muteUntil = Long.MAX_VALUE;                                           break;
        default: muteUntil = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1);  break;
      }

      listener.onMuted(muteUntil);
    });

    if (cancelListener != null) {
      builder.setOnCancelListener(dialog -> {
        cancelListener.run();
        dialog.dismiss();
      });
    }

    builder.show();

  }

  public interface MuteSelectionListener {
    public void onMuted(long until);
  }

}
