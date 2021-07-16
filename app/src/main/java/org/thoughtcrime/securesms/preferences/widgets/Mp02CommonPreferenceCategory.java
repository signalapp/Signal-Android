package org.thoughtcrime.securesms.preferences.widgets;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.TextView;

import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceViewHolder;

import org.thoughtcrime.securesms.R;

public class Mp02CommonPreferenceCategory extends PreferenceCategory {
  public Mp02CommonPreferenceCategory(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
    initialize();
  }

  public Mp02CommonPreferenceCategory(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    initialize();
  }

  public Mp02CommonPreferenceCategory(Context context, AttributeSet attrs) {
    super(context, attrs);
    initialize();
  }

  public Mp02CommonPreferenceCategory(Context context) {
    super(context);
    initialize();
  }

  private void initialize() {
    setLayoutResource(R.layout.mp02_common_preference_category_view);
  }

  @Override
  public void onBindViewHolder(PreferenceViewHolder holder) {
    super.onBindViewHolder(holder);
    TextView tv = holder.itemView.findViewById(R.id.pref_category_title);
    if (getTitle() != null) {
      tv.setText(getTitle());
    }
  }
}