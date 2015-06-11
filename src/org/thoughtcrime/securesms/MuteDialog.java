package org.thoughtcrime.securesms;

import android.content.Context;
import android.content.DialogInterface;
import android.support.annotation.NonNull;

import com.afollestad.materialdialogs.AlertDialogWrapper;

import java.util.concurrent.TimeUnit;

public class MuteDialog extends AlertDialogWrapper {

  private MuteDialog() {}

  public static void show(final Context context, final @NonNull MuteSelectionListener listener) {
    AlertDialogWrapper.Builder builder = new AlertDialogWrapper.Builder(context);
    builder.setTitle(R.string.MuteDialog_mute_notifications);
    builder.setItems(R.array.mute_durations, new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, final int which) {
        final long muteUntil;

        switch (which) {
          case 0:  muteUntil = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1); break;
          case 1:  muteUntil = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(2); break;
          case 2:  muteUntil = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1);  break;
          case 3:  muteUntil = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(7);  break;
          default: muteUntil = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1); break;
        }

        listener.onMuted(muteUntil);
      }
    });

    builder.show();

  }

  public interface MuteSelectionListener {
    public void onMuted(long until);
  }

}
