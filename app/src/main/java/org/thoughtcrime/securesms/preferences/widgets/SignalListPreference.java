package org.thoughtcrime.securesms.preferences.widgets;


import android.content.Context;
import android.os.Build;
import androidx.annotation.RequiresApi;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;
import android.util.AttributeSet;
import android.widget.TextView;

import org.thoughtcrime.securesms.R;

public class SignalListPreference extends ListPreference {

  private TextView                  rightSummary;
  private CharSequence              summary;
  private OnPreferenceClickListener clickListener;

  @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
  public SignalListPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
    initialize();
  }

  @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
  public SignalListPreference(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    initialize();
  }

  public SignalListPreference(Context context, AttributeSet attrs) {
    super(context, attrs);
    initialize();
  }

  public SignalListPreference(Context context) {
    super(context);
    initialize();
  }

  private void initialize() {
    setWidgetLayoutResource(R.layout.preference_right_summary_widget);
  }

  @Override
  public void onBindViewHolder(PreferenceViewHolder view) {
    super.onBindViewHolder(view);

    this.rightSummary = (TextView)view.findViewById(R.id.right_summary);
    setSummary(this.summary);
  }

  @Override
  public void setSummary(CharSequence summary) {
    super.setSummary(null);

    this.summary = summary;

    if (this.rightSummary != null) {
      this.rightSummary.setText(summary);
    }
  }

  @Override
  public void setOnPreferenceClickListener(OnPreferenceClickListener onPreferenceClickListener) {
    this.clickListener = onPreferenceClickListener;
  }

  @Override
  protected void onClick() {
    if (clickListener == null || !clickListener.onPreferenceClick(this)) {
      super.onClick();
    }
  }
}
