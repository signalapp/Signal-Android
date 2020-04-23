package org.thoughtcrime.securesms.preferences.widgets;


import android.content.Context;
import android.graphics.PorterDuff;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;

import org.thoughtcrime.securesms.R;

public class ContactPreference extends Preference {

  private ImageView messageButton;
  private ImageView callButton;
  private ImageView secureCallButton;
  private ImageView secureVideoButton;

  private Listener listener;
  private boolean secure;
  private boolean blocked;

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

    this.messageButton     = (ImageView) view.findViewById(R.id.message);
    this.callButton        = (ImageView) view.findViewById(R.id.call);
    this.secureCallButton  = (ImageView) view.findViewById(R.id.secure_call);
    this.secureVideoButton = (ImageView) view.findViewById(R.id.secure_video);

    if (listener != null) setListener(listener);
    setState(secure, blocked);
  }

  public void setState(boolean secure, boolean blocked) {
    this.secure = secure;

    if (secureCallButton != null)  secureCallButton.setVisibility(secure && !blocked ? View.VISIBLE : View.GONE);
    if (secureVideoButton != null) secureVideoButton.setVisibility(secure && !blocked ? View.VISIBLE : View.GONE);
    if (callButton != null)        callButton.setVisibility(secure || blocked ? View.GONE : View.VISIBLE);
    if (messageButton != null)     messageButton.setVisibility(blocked ? View.GONE : View.VISIBLE);

    int color;

    if (secure) {
      color = getContext().getResources().getColor(R.color.core_ultramarine);
    } else {
      color = getContext().getResources().getColor(R.color.grey_600);
    }

    if (messageButton != null)     messageButton.setColorFilter(color, PorterDuff.Mode.SRC_IN);
    if (secureCallButton != null)  secureCallButton.setColorFilter(color, PorterDuff.Mode.SRC_IN);
    if (secureVideoButton != null) secureVideoButton.setColorFilter(color, PorterDuff.Mode.SRC_IN);
    if (callButton != null)        callButton.setColorFilter(color, PorterDuff.Mode.SRC_IN);
  }

  public void setListener(Listener listener) {
    this.listener = listener;

    if (this.messageButton != null)     this.messageButton.setOnClickListener(v -> listener.onMessageClicked());
    if (this.secureCallButton != null)  this.secureCallButton.setOnClickListener(v -> listener.onSecureCallClicked());
    if (this.secureVideoButton != null) this.secureVideoButton.setOnClickListener(v -> listener.onSecureVideoClicked());
    if (this.callButton != null)        this.callButton.setOnClickListener(v -> listener.onInSecureCallClicked());
  }

  public interface Listener {
    public void onMessageClicked();
    public void onSecureCallClicked();
    public void onSecureVideoClicked();
    public void onInSecureCallClicked();
  }

}
