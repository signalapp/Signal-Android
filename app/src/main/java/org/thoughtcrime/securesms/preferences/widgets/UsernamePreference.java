package org.thoughtcrime.securesms.preferences.widgets;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import org.thoughtcrime.securesms.R;

public class UsernamePreference extends Preference {

  private View.OnLongClickListener onLongClickListener;

  public UsernamePreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
    initialize();
  }

  public UsernamePreference(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    initialize();
  }

  public UsernamePreference(Context context, AttributeSet attrs) {
    super(context, attrs);
    initialize();
  }

  public UsernamePreference(Context context) {
    super(context);
    initialize();
  }

  private void initialize() {
    setLayoutResource(R.layout.preference_username);
  }

  public void setOnLongClickListener(View.OnLongClickListener onLongClickListener) {
    this.onLongClickListener = onLongClickListener;
  }

  @Override
  public void onBindViewHolder(PreferenceViewHolder holder) {
    super.onBindViewHolder(holder);
    holder.itemView.setOnLongClickListener(onLongClickListener);
  }
}
