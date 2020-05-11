package org.thoughtcrime.securesms;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import org.thoughtcrime.securesms.util.ExpirationUtil;

import cn.carbswang.android.numberpickerview.library.NumberPickerView;

public class ExpirationDialog extends AlertDialog {

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
                          final @NonNull OnClickListener listener)
  {
    final View view = createNumberPickerView(context, currentExpiration);

    AlertDialog.Builder builder = new AlertDialog.Builder(context);
    builder.setTitle(context.getString(R.string.ExpirationDialog_disappearing_messages));
    builder.setView(view);
    builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
      int selected = ((NumberPickerView)view.findViewById(R.id.expiration_number_picker)).getValue();
      listener.onClick(context.getResources().getIntArray(R.array.expiration_times)[selected]);
    });
    builder.setNegativeButton(android.R.string.cancel, null);
    builder.show();
  }

  private static View createNumberPickerView(final Context context, final int currentExpiration) {
    final LayoutInflater   inflater                = LayoutInflater.from(context);
    final View             view                    = inflater.inflate(R.layout.expiration_dialog, null);
    final NumberPickerView numberPickerView        = view.findViewById(R.id.expiration_number_picker);
    final TextView         textView                = view.findViewById(R.id.expiration_details);
    final int[]            expirationTimes         = context.getResources().getIntArray(R.array.expiration_times);
    final String[]         expirationDisplayValues = new String[expirationTimes.length];

    int selectedIndex = expirationTimes.length - 1;

    for (int i=0;i<expirationTimes.length;i++) {
      expirationDisplayValues[i] = ExpirationUtil.getExpirationDisplayValue(context, expirationTimes[i]);

      if ((currentExpiration >= expirationTimes[i]) &&
          (i == expirationTimes.length -1 || currentExpiration < expirationTimes[i+1])) {
        selectedIndex = i;
      }
    }

    numberPickerView.setDisplayedValues(expirationDisplayValues);
    numberPickerView.setMinValue(0);
    numberPickerView.setMaxValue(expirationTimes.length-1);

    NumberPickerView.OnValueChangeListener listener = (picker, oldVal, newVal) -> {
      if (newVal == 0) {
        textView.setText(R.string.ExpirationDialog_your_messages_will_not_expire);
      } else {
        textView.setText(context.getString(R.string.ExpirationDialog_your_messages_will_disappear_s_after_they_have_been_seen, picker.getDisplayedValues()[newVal]));
      }
    };

    numberPickerView.setOnValueChangedListener(listener);
    numberPickerView.setValue(selectedIndex);
    listener.onValueChange(numberPickerView, selectedIndex, selectedIndex);

    return view;
  }

  public interface OnClickListener {
    public void onClick(int expirationTime);
  }

}
