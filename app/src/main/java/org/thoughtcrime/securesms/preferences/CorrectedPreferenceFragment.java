package org.thoughtcrime.securesms.preferences;


import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.DialogFragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroupAdapter;
import androidx.preference.PreferenceScreen;
import androidx.preference.PreferenceViewHolder;
import androidx.recyclerview.widget.RecyclerView;

import org.thoughtcrime.securesms.components.CustomDefaultPreference;
import org.thoughtcrime.securesms.conversation.v2.ViewUtil;
import org.thoughtcrime.securesms.preferences.widgets.ColorPickerPreference;
import org.thoughtcrime.securesms.preferences.widgets.ColorPickerPreferenceDialogFragmentCompat;

import network.loki.messenger.R;

public abstract class CorrectedPreferenceFragment extends PreferenceFragmentCompat {

  public static final int SINGLE_TYPE = 21;
  public static final int TOP_TYPE = 22;
  public static final int MIDDLE_TYPE = 23;
  public static final int BOTTOM_TYPE = 24;
  public static final int CATEGORY_TYPE = 25;

  public int horizontalPadding;
  public int verticalPadding;

  @Override
  public void onCreate(Bundle icicle) {
    super.onCreate(icicle);
    horizontalPadding = ViewUtil.dpToPx(requireContext(), 36);
    verticalPadding = ViewUtil.dpToPx(requireContext(), 8);
  }

  @Override
  public void onActivityCreated(Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);

    View lv = getView().findViewById(android.R.id.list);
    if (lv != null) lv.setPadding(0, 0, 0, 0);
    setDivider(null);
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

      @NonNull
      @Override
      public PreferenceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        PreferenceViewHolder viewHolder = super.onCreateViewHolder(parent, viewType);
        return viewHolder;
      }

      private int getPreferenceType(int position) {
        Preference preference = getItem(position);
        if (preference instanceof PreferenceCategory) {
          return CATEGORY_TYPE;
        }
        boolean isStart = isTop(position);
        boolean isEnd = isBottom(position);
        if (isStart && isEnd) {
          // always show full
          return SINGLE_TYPE;
        } else {
          if (isStart) {
            return TOP_TYPE;
          } else if (isEnd) {
            return BOTTOM_TYPE;
          } else {
            return MIDDLE_TYPE;
          }
        }
      }

      private boolean isTop(int position) {
        if (position == 0) {
          return true;
        }
        Preference previous = getItem(position - 1);
        return previous instanceof PreferenceCategory;
      }

      private boolean isBottom(int position) {
        int size = getItemCount();
        if (position == size - 1) {
          // last one
          return true;
        }
        Preference next = getItem(position + 1);
        return next instanceof PreferenceCategory;
      }

      public Drawable getBackground(Context context, int position) {
        int viewType = getPreferenceType(position);
        Drawable background;
        switch (viewType) {
          case SINGLE_TYPE:
            background = ContextCompat.getDrawable(context, R.drawable.preference_single);
            break;
          case TOP_TYPE:
            background = ContextCompat.getDrawable(context, R.drawable.preference_top);
            break;
          case MIDDLE_TYPE:
            background = ContextCompat.getDrawable(context, R.drawable.preference_middle);
            break;
          case BOTTOM_TYPE:
            background = ContextCompat.getDrawable(context, R.drawable.preference_bottom);
            break;
          default:
            background = null;
            break;
        }
        return background;
      }

      @Override
      public void onBindViewHolder(PreferenceViewHolder holder, int position) {
        super.onBindViewHolder(holder, position);
        Preference preference = getItem(position);
        if (preference instanceof PreferenceCategory) {
          ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams) holder.itemView.getLayoutParams();
          layoutParams.topMargin = 0;
          layoutParams.bottomMargin = 0;
          holder.itemView.setLayoutParams(layoutParams);
          setZeroPaddingToLayoutChildren(holder.itemView);
        } else {
          View iconFrame = holder.itemView.findViewById(R.id.icon_frame);
          if (iconFrame != null) {
            iconFrame.setVisibility(preference.getIcon() == null ? View.GONE : View.VISIBLE);
          }
          Drawable background = getBackground(holder.itemView.getContext(), position);
          holder.itemView.setBackground(background);
          TextView titleView = holder.itemView.findViewById(android.R.id.title);
          if (titleView != null) {
            ((TextView) titleView).setTypeface(Typeface.defaultFromStyle(Typeface.BOLD));
          }
          boolean isTop = isTop(position);
          boolean isBottom = isBottom(position);
          holder.itemView.setPadding(horizontalPadding, isTop ? verticalPadding : 0, horizontalPadding, isBottom ? verticalPadding : 0);
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
