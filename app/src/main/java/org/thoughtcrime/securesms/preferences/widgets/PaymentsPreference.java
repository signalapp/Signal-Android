package org.thoughtcrime.securesms.preferences.widgets;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import org.thoughtcrime.securesms.R;

public class PaymentsPreference extends Preference {

  private TextView unreadIndicator;
  private int      unreadCount;

  public PaymentsPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
    initialize();
  }

  public PaymentsPreference(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    initialize();
  }

  public PaymentsPreference(Context context, AttributeSet attrs) {
    super(context, attrs);
    initialize();
  }

  public PaymentsPreference(Context context) {
    super(context);
    initialize();
  }

  private void initialize() {
    setLayoutResource(R.layout.payments_preference);
  }

  @Override
  public void onBindViewHolder(PreferenceViewHolder holder) {
    super.onBindViewHolder(holder);

    unreadIndicator = holder.itemView.findViewById(R.id.unread_indicator);

    setUnreadCount(unreadCount);
  }

  public void setUnreadCount(int unreadCount) {
    this.unreadCount = unreadCount;

    if (unreadIndicator != null) {
      unreadIndicator.setVisibility(unreadCount > 0 ? View.VISIBLE : View.GONE);
      unreadIndicator.setText(String.valueOf(unreadCount));
    }
  }
}
