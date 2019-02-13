package org.thoughtcrime.securesms.components.registration;


import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.Build;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.thoughtcrime.securesms.R;

public class CallMeCountDownView extends android.support.v7.widget.AppCompatButton {

  private int countDown;

  public CallMeCountDownView(Context context) {
    super(context);
  }

  public CallMeCountDownView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public CallMeCountDownView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  public void startCountDown(int countDown) {
    this.countDown = countDown;
    updateCountDown();
  }

  public void setCallEnabled() {
    setText(R.string.RegistrationActivity_call);
    setEnabled(true);
    setAlpha(1.0f);
  }

  private void updateCountDown() {
    if (countDown > 0) {
      setEnabled(false);
      setAlpha(0.5f);

      countDown--;

      int minutesRemaining = countDown / 60;
      int secondsRemaining = countDown - (minutesRemaining * 60);

      setText(getResources().getString(R.string.RegistrationActivity_call_me_instead_available_in, minutesRemaining, secondsRemaining));
      postDelayed(this::updateCountDown, 1000);
    } else if (countDown == 0) {
      setCallEnabled();
    }
  }
}
