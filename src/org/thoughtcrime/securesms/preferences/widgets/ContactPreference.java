package org.thoughtcrime.securesms.preferences.widgets;


import android.content.Context;
import android.graphics.PorterDuff;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceViewHolder;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;

import org.thoughtcrime.securesms.R;

public class ContactPreference extends Preference {

  private ImageView messageButton;
  private ImageView callButton;
  private ImageView secureCallButton;

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
    this.callButton       = (ImageView) view.findViewById(R.id.call);
    this.secureCallButton = (ImageView) view.findViewById(R.id.secure_call);

    if (listener != null) setListener(listener);
    setSecure(secure);
  }

  public void setSecure(boolean secure) {
    this.secure = secure;

    if (secureCallButton != null) secureCallButton.setVisibility(secure ? View.VISIBLE : View.GONE);
    if (callButton != null)       callButton.setVisibility(secure ? View.GONE : View.VISIBLE);

    int color;

    if (secure) {
      color = getContext().getResources().getColor(R.color.textsecure_primary);
    } else {
      color = getContext().getResources().getColor(R.color.grey_600);
    }

    if (messageButton != null)    messageButton.setColorFilter(color, PorterDuff.Mode.SRC_IN);
    if (secureCallButton != null) secureCallButton.setColorFilter(color, PorterDuff.Mode.SRC_IN);
    if (callButton != null)       callButton.setColorFilter(color, PorterDuff.Mode.SRC_IN);
  }

  public void setListener(Listener listener) {
    this.listener = listener;

    if (this.messageButton != null)    this.messageButton.setOnClickListener(v -> listener.onMessageClicked());
    if (this.secureCallButton != null) this.secureCallButton.setOnClickListener(v -> listener.onSecureCallClicked());
    if (this.callButton != null)       this.callButton.setOnClickListener(v -> listener.onInSecureCallClicked());
  }

  public interface Listener {
    public void onMessageClicked();
    public void onSecureCallClicked();
    public void onInSecureCallClicked();
  }

}
