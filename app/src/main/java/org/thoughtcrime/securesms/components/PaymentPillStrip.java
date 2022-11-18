package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;

import org.thoughtcrime.securesms.R;

public class PaymentPillStrip extends ConstraintLayout {

  private FrameLayout   buttonStart;
  private FrameLayout   buttonEnd;

  public PaymentPillStrip(@NonNull Context context) {
    super(context);
  }

  public PaymentPillStrip(@NonNull Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
  }

  public PaymentPillStrip(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  public PaymentPillStrip(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();

    buttonStart = findViewById(R.id.button_start_frame);
    buttonEnd   = findViewById(R.id.button_end_frame);
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);

    if (buttonStart.getMeasuredWidth() > buttonEnd.getMinimumWidth()) {
      buttonEnd.setMinimumWidth(buttonStart.getMeasuredWidth());
    }

    if (buttonEnd.getMeasuredWidth() > buttonStart.getMinimumWidth()) {
      buttonStart.setMinimumWidth(buttonEnd.getMeasuredWidth());
    }

    if (buttonStart.getMeasuredHeight() > buttonEnd.getMinimumHeight()) {
      buttonEnd.setMinimumHeight(buttonStart.getMeasuredHeight());
    }

    if (buttonEnd.getMeasuredHeight() > buttonStart.getMinimumHeight()) {
      buttonStart.setMinimumHeight(buttonEnd.getMeasuredHeight());
    }

    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
  }
}
