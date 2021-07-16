package org.thoughtcrime.securesms;

import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import org.thoughtcrime.securesms.util.ExpirationUtil;

import java.util.Arrays;

import cn.carbswang.android.numberpickerview.library.NumberPickerView;

public class ExpirationDialog extends AlertDialog {

  private static int selectedResult = 0;

  protected ExpirationDialog(Context context) {
    super(context);
  }

  protected ExpirationDialog(Context context, int theme) {
    super(context, theme);
  }

  protected ExpirationDialog(Context context, boolean cancelable, OnCancelListener cancelListener) {
    super(context, cancelable, cancelListener);
  }

  public static void show(final Context context,
                          final int currentExpiration,
                          final @NonNull OnClickListener listener) {
    final int[] expirationTimes = context.getResources().getIntArray(R.array.expiration_times);
    final String[] expirationDisplayValues = new String[expirationTimes.length];
    int selectedIndex = expirationTimes.length - 1;
    for (int i = 0; i < expirationTimes.length; i++) {
      expirationDisplayValues[i] = ExpirationUtil.getExpirationDisplayValue(context, expirationTimes[i]);

      if ((currentExpiration >= expirationTimes[i]) &&
              (i == expirationTimes.length - 1 || currentExpiration < expirationTimes[i + 1])) {
        selectedIndex = i;
      }
    }

    AlertDialog.Builder builder = new AlertDialog.Builder(context);
    builder.setTitle(context.getString(R.string.ExpirationDialog_disappearing_messages));
    builder.setSingleChoiceItems(expirationDisplayValues, selectedIndex, new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        selectedResult = which;
      }
    });
    builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
      listener.onClick(context.getResources().getIntArray(R.array.expiration_times)[selectedResult]);
    });
    builder.setCancelable(false);
    builder.show();
  }

  public interface OnClickListener {
    void onClick(int expirationTime);
  }

}
