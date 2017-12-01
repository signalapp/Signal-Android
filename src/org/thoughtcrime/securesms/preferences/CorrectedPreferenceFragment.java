package org.thoughtcrime.securesms.preferences;


import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.view.View;

import org.thoughtcrime.securesms.components.CustomDefaultPreference;
import org.thoughtcrime.securesms.preferences.widgets.ColorPickerPreference;
import org.thoughtcrime.securesms.preferences.widgets.ColorPickerPreferenceDialogFragmentCompat;

public abstract class CorrectedPreferenceFragment extends PreferenceFragmentCompat {

  @Override
  public void onCreate(Bundle icicle) {
    super.onCreate(icicle);
  }

  @Override
  public void onActivityCreated(Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);

    View lv = getView().findViewById(android.R.id.list);
    if (lv != null) lv.setPadding(0, 0, 0, 0);
  }

  @Override
  public void onDisplayPreferenceDialog(Preference preference) {
    DialogFragment dialogFragment = null;

    if (preference instanceof ColorPickerPreference) {
      dialogFragment = ColorPickerPreferenceDialogFragmentCompat.newInstance(preference.getKey());
    } else if (preference instanceof CustomDefaultPreference) {
      dialogFragment = CustomDefaultPreference.CustomDefaultPreferenceDialogFragmentCompat.newInstance(preference.getKey());
    }

    if (dialogFragment != null) {
      dialogFragment.setTargetFragment(this, 0);
      dialogFragment.show(getFragmentManager(), "android.support.v7.preference.PreferenceFragment.DIALOG");
    } else {
      super.onDisplayPreferenceDialog(preference);
    }
  }


}
