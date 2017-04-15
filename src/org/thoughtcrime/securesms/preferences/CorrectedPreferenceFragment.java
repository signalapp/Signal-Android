package org.thoughtcrime.securesms.preferences;


import android.os.Bundle;
import android.support.v4.preference.PreferenceFragment;
import android.view.View;

public class CorrectedPreferenceFragment extends PreferenceFragment {

  @Override
  public void onActivityCreated(Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);

    View lv = getView().findViewById(android.R.id.list);
    if (lv != null) lv.setPadding(0, 0, 0, 0);
  }

}
