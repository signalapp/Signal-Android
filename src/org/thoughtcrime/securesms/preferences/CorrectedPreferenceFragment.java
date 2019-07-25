package org.thoughtcrime.securesms.preferences;


import android.annotation.SuppressLint;
import android.os.Bundle;
import androidx.fragment.app.DialogFragment;
import androidx.core.view.ViewCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroupAdapter;
import androidx.preference.PreferenceScreen;
import androidx.preference.PreferenceViewHolder;
import androidx.recyclerview.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;

import org.thoughtcrime.securesms.R;
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

  @Override
  protected RecyclerView.Adapter onCreateAdapter(PreferenceScreen preferenceScreen) {
    return new PreferenceGroupAdapter(preferenceScreen) {
      @SuppressLint("RestrictedApi")
      @Override
      public void onBindViewHolder(PreferenceViewHolder holder, int position) {
        super.onBindViewHolder(holder, position);
        Preference preference = getItem(position);
        if (preference instanceof PreferenceCategory) {
          setZeroPaddingToLayoutChildren(holder.itemView);
        } else {
          View iconFrame = holder.itemView.findViewById(R.id.icon_frame);
          if (iconFrame != null) {
            iconFrame.setVisibility(preference.getIcon() == null ? View.GONE : View.VISIBLE);
          }
        }
      }
    };
  }

  private void setZeroPaddingToLayoutChildren(View view) {
    if (!(view instanceof ViewGroup)) return;

    ViewGroup viewGroup = (ViewGroup) view;
    for (int i = 0; i < viewGroup.getChildCount(); i++) {
      setZeroPaddingToLayoutChildren(viewGroup.getChildAt(i));
      ViewCompat.setPaddingRelative(viewGroup, 0, viewGroup.getPaddingTop(), ViewCompat.getPaddingEnd(viewGroup), viewGroup.getPaddingBottom());
    }
  }
}
