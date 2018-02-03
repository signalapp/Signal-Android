package org.thoughtcrime.securesms.components.registration;


import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.Build;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.thoughtcrime.securesms.R;

public class CallMeCountDownView extends RelativeLayout {

  private ImageView phone;
  private TextView  callMeText;
  private TextView  availableInText;
  private TextView  countDownText;

  private int             countDown;
  private OnClickListener listener;

  public CallMeCountDownView(Context context) {
    super(context);
    initialize();
  }

  public CallMeCountDownView(Context context, AttributeSet attrs) {
    super(context, attrs);
    initialize();
  }

  public CallMeCountDownView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    initialize();
  }

  @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
  public CallMeCountDownView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
    initialize();
  }

  private void initialize() {
    inflate(getContext(), R.layout.registration_call_me_view, this);

    this.phone           = findViewById(R.id.phone_icon);
    this.callMeText      = findViewById(R.id.call_me_text);
    this.availableInText = findViewById(R.id.available_in_text);
    this.countDownText   = findViewById(R.id.countdown);
  }

  public void setOnClickListener(@Nullable OnClickListener listener) {
    this.listener = listener;
  }

  public void startCountDown(int countDown) {
    setVisibility(View.VISIBLE);
    this.phone.setColorFilter(null);
    this.phone.setOnClickListener(null);

    this.callMeText.setTextColor(getResources().getColor(R.color.grey_700));
    this.callMeText.setOnClickListener(null);

    this.availableInText.setVisibility(View.VISIBLE);
    this.countDownText.setVisibility(View.VISIBLE);

    this.countDown = countDown;
    updateCountDown();
  }

  public void setCallEnabled() {
    setVisibility(View.VISIBLE);
    this.phone.setColorFilter(new PorterDuffColorFilter(getResources().getColor(R.color.signal_primary), PorterDuff.Mode.SRC_IN));
    this.callMeText.setTextColor(getResources().getColor(R.color.signal_primary));

    this.availableInText.setVisibility(View.GONE);
    this.countDownText.setVisibility(View.GONE);

    this.phone.setOnClickListener(v -> handlePhoneCallRequest());
    this.callMeText.setOnClickListener(v -> handlePhoneCallRequest());
  }

  private void updateCountDown() {
    if (countDown > 0) {
      countDown--;

      int minutesRemaining = countDown / 60;
      int secondsRemaining = countDown - (minutesRemaining * 60);

      countDownText.setText(String.format("%02d:%02d", minutesRemaining, secondsRemaining));
      countDownText.postDelayed(this::updateCountDown, 1000);
    } else if (countDown == 0) {
      setCallEnabled();
    }
  }

  private void handlePhoneCallRequest() {
    if (listener != null) listener.onClick(this);
  }

}
