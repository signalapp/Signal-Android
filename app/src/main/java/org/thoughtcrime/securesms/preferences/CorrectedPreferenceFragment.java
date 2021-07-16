package org.thoughtcrime.securesms.preferences;


import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.view.ViewCompat;
import androidx.fragment.app.DialogFragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceGroupAdapter;
import androidx.preference.PreferenceScreen;
import androidx.preference.PreferenceViewHolder;
import androidx.recyclerview.widget.RecyclerView;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.CustomDefaultPreference;
import org.thoughtcrime.securesms.components.mp02anim.ItemAnimViewController;
import org.thoughtcrime.securesms.preferences.widgets.ColorPickerPreference;
import org.thoughtcrime.securesms.preferences.widgets.ColorPickerPreferenceDialogFragmentCompat;

public abstract class CorrectedPreferenceFragment extends PreferenceFragmentCompat {

  protected static final String PREF_STATUS_ON = " ON";
  protected static final String PREF_STATUS_OFF = " OFF";

  private ItemAnimViewController mItemAnimViewController;
  private ViewGroup mContainer;
  protected int mItemFocusHeight;
  protected int mItemNormalHeight;
  protected int mItemFocusTextSize;
  protected int mItemNormalTextSize;
  protected int mItemFocusPadding;
  protected int mItemNormalPadding;
  protected int mItemStartY;
  protected boolean mIsScrollUp;
  private RecyclerView list;
  private int focus=0;

  @Override
  public void onCreate(Bundle icicle) {
    super.onCreate(icicle);
  }

  @SuppressLint("NewApi")
  @Override
  public void onActivityCreated(Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);
    requireView().setBackgroundColor(getResources().getColor(R.color.black));
    RecyclerView rv = getListView();
    rv.setClipToPadding(false);
    rv.setClipChildren(false);
    rv.setPadding(0, 76, 0, 200);
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    View root = super.onCreateView(inflater, container, savedInstanceState);
    root.findViewById(android.R.id.list_container).setBackgroundColor(getResources().getColor(R.color.sim_background));
    list = getListView();
    mContainer = (ViewGroup) list.getParent();
    mItemFocusHeight = 56;
    mItemNormalHeight = 32;
    mItemFocusTextSize = 40;
    mItemNormalTextSize = 24;
    mItemFocusPadding = 5;
    mItemNormalPadding = 30;
    mItemStartY = 76;
    return root;
  }

  public int getFocus() {
    return focus;
  }

  public void setFocus(int focus) {
    this.focus = focus;
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
  @SuppressLint("RestrictedApi")
  protected RecyclerView.Adapter onCreateAdapter(PreferenceScreen preferenceScreen) {
    return new PreferenceGroupAdapter(preferenceScreen) {
      @Override
      public void onBindViewHolder(PreferenceViewHolder holder, int position) {
        super.onBindViewHolder(holder, position);
        holder.itemView.setTag(position);
        Preference preference = getItem(position);
        if (preference instanceof PreferenceCategory) {
          setZeroPaddingToLayoutChildren(holder.itemView);
        } else {
          View iconFrame = holder.itemView.findViewById(R.id.icon_frame);
          if (iconFrame != null) {
            iconFrame.setVisibility(preference.getIcon() == null ? View.GONE : View.VISIBLE);
          }
        }
        if (position==focus)
        {
          holder.itemView.requestFocus();
        }
      }
    };
  }

  public boolean onKeyDown(KeyEvent event) {
//    RecyclerView rv = getListView();

    return false;
  }

  private void setZeroPaddingToLayoutChildren(View view) {
    if (!(view instanceof ViewGroup)) return;

    ViewGroup viewGroup = (ViewGroup) view;
    for (int i = 0; i < viewGroup.getChildCount(); i++) {
      setZeroPaddingToLayoutChildren(viewGroup.getChildAt(i));
      ViewCompat.setPaddingRelative(viewGroup, 0, viewGroup.getPaddingTop(), ViewCompat.getPaddingEnd(viewGroup), viewGroup.getPaddingBottom());
    }
  }

  protected PreferenceGroup getPreferenceGroup() {
    return getPreferenceScreen();
  }

  protected ItemAnimViewController getParentAnimViewController() {
    return new ItemAnimViewController<>(mContainer, mItemFocusTextSize, mItemFocusHeight, mItemStartY);
  }
}
