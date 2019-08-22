package org.thoughtcrime.securesms.components.registration;


import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.os.Build;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.OvershootInterpolator;
import android.view.animation.TranslateAnimation;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.R;

import java.util.ArrayList;
import java.util.List;

public class VerificationCodeView extends FrameLayout {

  private final List<TextView> codes      = new ArrayList<>(6);
  private final List<View>     containers = new ArrayList<>(6);

  private OnCodeEnteredListener listener;
  private int index = 0;

  public VerificationCodeView(Context context) {
    super(context);
    initialize(context, null);
  }

  public VerificationCodeView(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    initialize(context, attrs);
  }

  public VerificationCodeView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    initialize(context, attrs);
  }

  @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
  public VerificationCodeView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
    initialize(context, attrs);
  }

  private void initialize(@NonNull Context context, @Nullable AttributeSet attrs) {
    inflate(context, R.layout.verification_code_view, this);

    TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.VerificationCodeView);

    try {
      TextView separator = findViewById(R.id.separator);

      this.codes.add(findViewById(R.id.code_zero));
      this.codes.add(findViewById(R.id.code_one));
      this.codes.add(findViewById(R.id.code_two));
      this.codes.add(findViewById(R.id.code_three));
      this.codes.add(findViewById(R.id.code_four));
      this.codes.add(findViewById(R.id.code_five));

      this.containers.add(findViewById(R.id.container_zero));
      this.containers.add(findViewById(R.id.container_one));
      this.containers.add(findViewById(R.id.container_two));
      this.containers.add(findViewById(R.id.container_three));
      this.containers.add(findViewById(R.id.container_four));
      this.containers.add(findViewById(R.id.container_five));

      Stream.of(codes).forEach(textView -> textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, typedArray.getDimension(R.styleable.VerificationCodeView_vcv_textSize, 30)));
      Stream.of(codes).forEach(textView -> textView.setTextColor(typedArray.getColor(R.styleable.VerificationCodeView_vcv_textColor, Color.GRAY)));

      separator.setTextSize(TypedValue.COMPLEX_UNIT_SP, typedArray.getDimension(R.styleable.VerificationCodeView_vcv_textSize, 30));
      separator.setTextColor(typedArray.getColor(R.styleable.VerificationCodeView_vcv_textColor, Color.GRAY));

    } finally {
      if (typedArray != null) typedArray.recycle();
    }
  }

  @MainThread
  public void setOnCompleteListener(OnCodeEnteredListener listener) {
    this.listener = listener;
  }

  @MainThread
  public void append(int value) {
    if (index >= codes.size()) return;

    setInactive(containers);
    setActive(containers.get(index));

    TextView codeView = codes.get(index++);

    Animation translateIn = new TranslateAnimation(0, 0, codeView.getHeight(), 0);
    translateIn.setInterpolator(new OvershootInterpolator());
    translateIn.setDuration(500);

    Animation fadeIn = new AlphaAnimation(0, 1);
    fadeIn.setDuration(200);

    AnimationSet animationSet = new AnimationSet(false);
    animationSet.addAnimation(fadeIn);
    animationSet.addAnimation(translateIn);
    animationSet.reset();
    animationSet.setStartTime(0);

    codeView.setText(String.valueOf(value));
    codeView.clearAnimation();
    codeView.startAnimation(animationSet);

    if (index == codes.size() && listener != null) {
      listener.onCodeComplete(Stream.of(codes).map(TextView::getText).collect(Collectors.joining()));
    }
  }

  @MainThread
  public void delete() {
    if (index <= 0) return;
    codes.get(--index).setText("");
    setInactive(containers);
    setActive(containers.get(index));
  }

  @MainThread
  public void clear() {
    if (index != 0) {
      Stream.of(codes).forEach(code -> code.setText(""));
      index = 0;
    }
    setInactive(containers);
  }

  private void setInactive(List<View> views) {
    Stream.of(views).forEach(c -> c.setBackgroundResource(R.drawable.labeled_edit_text_background_inactive));
  }

  private void setActive(@NonNull View container) {
    container.setBackgroundResource(R.drawable.labeled_edit_text_background_active);
  }

  public interface OnCodeEnteredListener {
    void onCodeComplete(@NonNull String code);
  }
}
