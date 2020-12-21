package org.thoughtcrime.securesms.preferences.widgets;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.Preference;

import org.thoughtcrime.securesms.R;

public class SignalPreference extends Preference {

  public SignalPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
    initialize();
  }

  public SignalPreference(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    initialize();
  }

  public SignalPreference(Context context, AttributeSet attrs) {
    super(context, attrs);
    initialize();
  }

  public SignalPreference(Context context) {
    super(context);
    initialize();
  }

  private void initialize() {
    setLayoutResource(R.layout.preference_right_summary);
  }
}
