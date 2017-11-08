package org.thoughtcrime.securesms.components.registration;


import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.os.Build;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.OvershootInterpolator;
import android.view.animation.TranslateAnimation;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.ViewUtil;

import java.util.ArrayList;
import java.util.List;

public class VerificationCodeView extends FrameLayout {

  private final List<View>     spaces     = new ArrayList<>(6);
  private final List<TextView> codes      = new ArrayList<>(6);
  private final List<View>     containers = new ArrayList<>(7);

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

      this.spaces.add(findViewById(R.id.space_zero));
      this.spaces.add(findViewById(R.id.space_one));
      this.spaces.add(findViewById(R.id.space_two));
      this.spaces.add(findViewById(R.id.space_three));
      this.spaces.add(findViewById(R.id.space_four));
      this.spaces.add(findViewById(R.id.space_five));

      this.codes.add(findViewById(R.id.code_zero));
      this.codes.add(findViewById(R.id.code_one));
      this.codes.add(findViewById(R.id.code_two));
      this.codes.add(findViewById(R.id.code_three));
      this.codes.add(findViewById(R.id.code_four));
      this.codes.add(findViewById(R.id.code_five));

      this.containers.add(findViewById(R.id.container_zero));
      this.containers.add(findViewById(R.id.container_one));
      this.containers.add(findViewById(R.id.container_two));
      this.containers.add(findViewById(R.id.separator_container));
      this.containers.add(findViewById(R.id.container_three));
      this.containers.add(findViewById(R.id.container_four));
      this.containers.add(findViewById(R.id.container_five));

      Stream.of(spaces).forEach(view -> view.setBackgroundColor(typedArray.getColor(R.styleable.VerificationCodeView_vcv_inputColor, Color.BLACK)));
      Stream.of(spaces).forEach(view -> view.setLayoutParams(new LinearLayout.LayoutParams(typedArray.getDimensionPixelSize(R.styleable.VerificationCodeView_vcv_inputWidth, ViewUtil.dpToPx(context, 20)),
                                                                                           typedArray.getDimensionPixelSize(R.styleable.VerificationCodeView_vcv_inputHeight, ViewUtil.dpToPx(context, 1)))));
      Stream.of(codes).forEach(textView -> textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, typedArray.getDimension(R.styleable.VerificationCodeView_vcv_textSize, 30)));
      Stream.of(codes).forEach(textView -> textView.setTextColor(typedArray.getColor(R.styleable.VerificationCodeView_vcv_textColor, Color.GRAY)));

      Stream.of(containers).forEach(view -> {
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams)view.getLayoutParams();
        params.setMargins(typedArray.getDimensionPixelSize(R.styleable.VerificationCodeView_vcv_spacing, ViewUtil.dpToPx(context, 5)),
                          params.topMargin, params.rightMargin, params.bottomMargin);
        view.setLayoutParams(params);
      });

      separator.setTextSize(TypedValue.COMPLEX_UNIT_SP, typedArray.getDimension(R.styleable.VerificationCodeView_vcv_textSize, 30));
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
  }

  @MainThread
  public void clear() {
    if (index != 0) {
      Stream.of(codes).forEach(code -> code.setText(""));
      index = 0;
    }
  }

  public interface OnCodeEnteredListener {
    void onCodeComplete(@NonNull String code);
  }
}
