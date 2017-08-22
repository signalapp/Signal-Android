package org.thoughtcrime.securesms.preferences;


import android.content.Context;
import android.os.Build;
import android.preference.RingtonePreference;
import android.support.annotation.RequiresApi;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import org.thoughtcrime.securesms.R;

public class SignalRingtonePreference extends AdvancedRingtonePreference {

  private TextView rightSummary;
  private CharSequence summary;

  @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
  public SignalRingtonePreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
    initialize();
  }

  public SignalRingtonePreference(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    initialize();
  }

  public SignalRingtonePreference(Context context, AttributeSet attrs) {
    super(context, attrs);
    initialize();
  }

  public SignalRingtonePreference(Context context) {
    super(context);
    initialize();
  }

  private void initialize() {
    setWidgetLayoutResource(R.layout.preference_right_summary_widget);
  }

  @Override
  protected void onBindView(View view) {
    super.onBindView(view);

    this.rightSummary = (TextView)view.findViewById(R.id.right_summary);
    setSummary(summary);
  }

  @Override
  public void setSummary(CharSequence summary) {
    this.summary = summary;

    super.setSummary(null);

    if (rightSummary != null) {
      rightSummary.setText(summary);
    }
  }

}
