package org.thoughtcrime.securesms.preferences.widgets;


import android.content.Context;
import android.graphics.PorterDuff;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;

import network.loki.messenger.R;

public class ContactPreference extends Preference {

  private ImageView messageButton;

  private Listener listener;
  private boolean secure;

  public ContactPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
    initialize();
  }

  public ContactPreference(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    initialize();
  }

  public ContactPreference(Context context, AttributeSet attrs) {
    super(context, attrs);
    initialize();
  }

  public ContactPreference(Context context) {
    super(context);
    initialize();
  }

  private void initialize() {
    setWidgetLayoutResource(R.layout.recipient_preference_contact_widget);
  }

  @Override
  public void onBindViewHolder(PreferenceViewHolder view) {
    super.onBindViewHolder(view);

    this.messageButton    = (ImageView) view.findViewById(R.id.message);

    if (listener != null) setListener(listener);
    setSecure(secure);
  }

  public void setSecure(boolean secure) {
    this.secure = secure;

    int color;

    if (secure) {
      color = getContext().getResources().getColor(R.color.textsecure_primary);
    } else {
      color = getContext().getResources().getColor(R.color.grey_600);
    }

    if (messageButton != null)    messageButton.setColorFilter(color, PorterDuff.Mode.SRC_IN);
  }

  public void setListener(Listener listener) {
    this.listener = listener;

    if (this.messageButton != null)    this.messageButton.setOnClickListener(v -> listener.onMessageClicked());
  }

  public interface Listener {
    public void onMessageClicked();
    public void onSecureCallClicked();
    public void onInSecureCallClicked();
  }

}
