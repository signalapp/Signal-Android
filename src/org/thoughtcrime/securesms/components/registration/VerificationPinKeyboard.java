package org.thoughtcrime.securesms.components.registration;


import android.content.Context;
import android.graphics.PorterDuff;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.OvershootInterpolator;
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.concurrent.ListenableFuture;
import org.thoughtcrime.securesms.util.concurrent.SettableFuture;

public class VerificationPinKeyboard extends FrameLayout {

  private KeyboardView keyboardView;
  private ProgressBar  progressBar;
  private ImageView    successView;
  private ImageView    failureView;

  private OnKeyPressListener listener;

  public VerificationPinKeyboard(@NonNull Context context) {
    super(context);
    initialize();
  }

  public VerificationPinKeyboard(@NonNull Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    initialize();
  }

  public VerificationPinKeyboard(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    initialize();
  }

  @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
  public VerificationPinKeyboard(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
    initialize();
  }

  private void initialize() {
    inflate(getContext(), R.layout.verification_pin_keyboard_view, this);

    this.keyboardView = findViewById(R.id.keyboard_view);
    this.progressBar  = findViewById(R.id.progress);
    this.successView  = findViewById(R.id.success);
    this.failureView  = findViewById(R.id.failure); ;

    keyboardView.setPreviewEnabled(false);
    keyboardView.setKeyboard(new Keyboard(getContext(), R.xml.pin_keyboard));
    keyboardView.setOnKeyboardActionListener(new KeyboardView.OnKeyboardActionListener() {
      @Override
      public void onPress(int primaryCode) {
        if (listener != null) listener.onKeyPress(primaryCode);
      }
      @Override
      public void onRelease(int primaryCode) {}
      @Override
      public void onKey(int primaryCode, int[] keyCodes) {}
      @Override
      public void onText(CharSequence text) {}
      @Override
      public void swipeLeft() {}
      @Override
      public void swipeRight() {}
      @Override
      public void swipeDown() {}
      @Override
      public void swipeUp() {}
    });

    displayKeyboard();
  }

  public void setOnKeyPressListener(@Nullable OnKeyPressListener listener) {
    this.listener = listener;
  }

  public void displayKeyboard() {
    this.keyboardView.setVisibility(View.VISIBLE);
    this.progressBar.setVisibility(View.GONE);
    this.successView.setVisibility(View.GONE);
    this.failureView.setVisibility(View.GONE);
  }

  public void displayProgress() {
    this.keyboardView.setVisibility(View.INVISIBLE);
    this.progressBar.setVisibility(View.VISIBLE);
    this.successView.setVisibility(View.GONE);
    this.failureView.setVisibility(View.GONE);
  }

  public ListenableFuture<Boolean> displaySuccess() {
    SettableFuture<Boolean> result = new SettableFuture<>();

    this.keyboardView.setVisibility(View.INVISIBLE);
    this.progressBar.setVisibility(View.GONE);
    this.failureView.setVisibility(View.GONE);

    this.successView.getBackground().setColorFilter(getResources().getColor(R.color.green_500), PorterDuff.Mode.SRC_IN);

    ScaleAnimation scaleAnimation = new ScaleAnimation(0, 1, 0, 1,
                                                       ScaleAnimation.RELATIVE_TO_SELF, 0.5f,
                                                       ScaleAnimation.RELATIVE_TO_SELF, 0.5f);
    scaleAnimation.setInterpolator(new OvershootInterpolator());
    scaleAnimation.setDuration(800);
    scaleAnimation.setAnimationListener(new Animation.AnimationListener() {
      @Override
      public void onAnimationStart(Animation animation) {}

      @Override
      public void onAnimationEnd(Animation animation) {
        result.set(true);
      }

      @Override
      public void onAnimationRepeat(Animation animation) {}
    });

    ViewUtil.animateIn(this.successView, scaleAnimation);
    return result;
  }

  public ListenableFuture<Boolean> displayFailure() {
    SettableFuture<Boolean> result = new SettableFuture<>();

    this.keyboardView.setVisibility(View.INVISIBLE);
    this.progressBar.setVisibility(View.GONE);
    this.failureView.setVisibility(View.GONE);

    this.failureView.getBackground().setColorFilter(getResources().getColor(R.color.red_500), PorterDuff.Mode.SRC_IN);
    this.failureView.setVisibility(View.VISIBLE);

    TranslateAnimation shake = new TranslateAnimation(0, 30, 0, 0);
    shake.setDuration(50);
    shake.setRepeatCount(7);
    shake.setAnimationListener(new Animation.AnimationListener() {
      @Override
      public void onAnimationStart(Animation animation) {}

      @Override
      public void onAnimationEnd(Animation animation) {
        result.set(true);
      }

      @Override
      public void onAnimationRepeat(Animation animation) {}
    });

    this.failureView.startAnimation(shake);

    return result;
  }

  public interface OnKeyPressListener {
    void onKeyPress(int keyCode);
  }
}
