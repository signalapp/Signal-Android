package org.thoughtcrime.securesms.preferences.widgets;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.preference.PreferenceDialogFragmentCompat;

import com.takisoft.colorpicker.ColorPickerDialog;
import com.takisoft.colorpicker.OnColorSelectedListener;

public class ColorPickerPreferenceDialogFragmentCompat extends PreferenceDialogFragmentCompat implements OnColorSelectedListener {

  private int pickedColor;

  public static ColorPickerPreferenceDialogFragmentCompat newInstance(String key) {
    ColorPickerPreferenceDialogFragmentCompat fragment = new ColorPickerPreferenceDialogFragmentCompat();
    Bundle b = new Bundle(1);
    b.putString(PreferenceDialogFragmentCompat.ARG_KEY, key);
    fragment.setArguments(b);
    return fragment;
  }


  @NonNull
  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    ColorPickerPreference pref = getColorPickerPreference();

    ColorPickerDialog.Params params = new ColorPickerDialog.Params.Builder(getContext())
        .setSelectedColor(pref.getColor())
        .setColors(pref.getColors())
        .setColorContentDescriptions(pref.getColorDescriptions())
        .setSize(pref.getSize())
        .setSortColors(pref.isSortColors())
        .setColumns(pref.getColumns())
        .build();

    ColorPickerDialog dialog = new ColorPickerDialog(getActivity(), this, params);
    dialog.setTitle(pref.getDialogTitle());

    return dialog;
  }

  @Override
  public void onDialogClosed(boolean positiveResult) {
    ColorPickerPreference preference = getColorPickerPreference();

    if (positiveResult) {
      preference.setColor(pickedColor);
    }
  }

  @Override
  public void onColorSelected(int color) {
    this.pickedColor = color;

    super.onClick(getDialog(), DialogInterface.BUTTON_POSITIVE);
  }

  ColorPickerPreference getColorPickerPreference() {
    return (ColorPickerPreference) getPreference();
  }
}