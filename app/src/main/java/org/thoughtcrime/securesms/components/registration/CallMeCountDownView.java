package org.thoughtcrime.securesms.components.registration;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.R;

import java.util.concurrent.TimeUnit;

public class CallMeCountDownView extends androidx.appcompat.widget.AppCompatButton {

  private long countDownToTime;
  @Nullable
  private Listener listener;

  public CallMeCountDownView(Context context) {
    super(context);
  }

  public CallMeCountDownView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public CallMeCountDownView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  /**
   * Starts a count down to the specified {@param time}.
   */
  public void startCountDownTo(long time) {
    if (time > 0) {
      this.countDownToTime = time;
      updateCountDown();
    }
  }

  public void setCallEnabled() {
    setText(R.string.RegistrationActivity_call);
    setEnabled(true);
    setAlpha(1.0f);
  }

  private void updateCountDown() {
    final long remainingMillis = countDownToTime - System.currentTimeMillis();

    if (remainingMillis > 0) {
      setEnabled(false);
      setAlpha(0.5f);

      int totalRemainingSeconds = (int) TimeUnit.MILLISECONDS.toSeconds(remainingMillis);
      int minutesRemaining      = totalRemainingSeconds / 60;
      int secondsRemaining      = totalRemainingSeconds % 60;

      setText(getResources().getString(R.string.RegistrationActivity_call_me_instead_available_in, minutesRemaining, secondsRemaining));

      if (listener != null) {
        listener.onRemaining(this, totalRemainingSeconds);
      }

      postDelayed(this::updateCountDown, 250);
    } else {
      setCallEnabled();
    }
  }

  public void setListener(@Nullable Listener listener) {
    this.listener = listener;
  }

  public interface Listener {
    void onRemaining(@NonNull CallMeCountDownView view, int secondsRemaining);
  }
}
