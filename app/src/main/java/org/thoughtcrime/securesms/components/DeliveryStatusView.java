package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

import org.signal.core.util.DimensionUnit;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.ViewUtil;

/**
 * View responsible for displaying the delivery status (NONE, PENDING, SENT, DELIVERED, READ) of a given outgoing message.
 * <p>
 * This view manipulates its start / end padding to properly place the corresponding icon, and also performs a rotation
 * animation on itself in the pending mode. Thus, users should be aware that padding values set in XML will be overwritten.
 * <p>
 * If you need to control the horizontal spacing of this view, utilize margins instead.
 */
public class DeliveryStatusView extends AppCompatImageView {

  private static final String STATE_KEY = "DeliveryStatusView.STATE";
  private static final String ROOT_KEY  = "DeliveryStatusView.ROOT";

  private final int horizontalPadding = (int) DimensionUnit.DP.toPixels(2);

  private RotateAnimation rotationAnimation;

  private State state = State.NONE;

  public DeliveryStatusView(Context context) {
    this(context, null);
  }

  public DeliveryStatusView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public DeliveryStatusView(final Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);

    if (attrs != null) {
      TypedArray typedArray = context.getTheme().obtainStyledAttributes(attrs, R.styleable.DeliveryStatusView, 0, 0);
      setTint(typedArray.getColor(R.styleable.DeliveryStatusView_iconColor, getResources().getColor(R.color.core_white)));
      typedArray.recycle();
    }

    setNone();
  }

  @Override
  protected void onRestoreInstanceState(Parcelable state) {
    if (state instanceof Bundle) {
      Bundle stateBundle = (Bundle) state;
      State  s           = State.fromCode(stateBundle.getInt(STATE_KEY, State.NONE.code));

      switch (s) {
        case NONE:
          setNone();
          break;
        case PENDING:
          setPending();
          break;
        case SENT:
          setSent();
          break;
        case DELIVERED:
          setDelivered();
          break;
        case READ:
          setRead();
          break;
      }

      Parcelable root = stateBundle.getParcelable(ROOT_KEY);
      super.onRestoreInstanceState(root);
    } else {
      super.onRestoreInstanceState(state);
    }
  }

  @Override
  protected @Nullable Parcelable onSaveInstanceState() {
    Parcelable root        = super.onSaveInstanceState();
    Bundle     stateBundle = new Bundle();

    stateBundle.putParcelable(ROOT_KEY, root);
    stateBundle.putInt(STATE_KEY, state.code);

    return stateBundle;
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    if (state == State.PENDING && rotationAnimation == null) {
      final float pivotXValue;
      if (ViewUtil.isLtr(this)) {
        pivotXValue = (w - getPaddingEnd()) / 2f;
      } else {
        pivotXValue = ((w - getPaddingEnd()) / 2f) + getPaddingEnd();
      }

      final float pivotYValue = (h - getPaddingTop() - getPaddingBottom()) / 2f;

      rotationAnimation = new RotateAnimation(0, 360f,
                                              Animation.ABSOLUTE, pivotXValue,
                                              Animation.ABSOLUTE, pivotYValue);

      rotationAnimation.setInterpolator(new LinearInterpolator());
      rotationAnimation.setDuration(1500);
      rotationAnimation.setRepeatCount(Animation.INFINITE);

      startAnimation(rotationAnimation);
    }
  }

  @Override
  public void clearAnimation() {
    super.clearAnimation();
    rotationAnimation = null;
  }

  public void setNone() {
    state = State.NONE;
    clearAnimation();
    setVisibility(View.GONE);
  }

  public boolean isPending() {
    return state == State.PENDING;
  }

  public void setPending() {
    state = State.PENDING;
    setVisibility(View.VISIBLE);
    ViewUtil.setPaddingStart(this, 0);
    ViewUtil.setPaddingEnd(this, horizontalPadding);
    setImageResource(R.drawable.ic_delivery_status_sending);
  }

  public void setSent() {
    state = State.SENT;
    setVisibility(View.VISIBLE);
    ViewUtil.setPaddingStart(this, horizontalPadding);
    ViewUtil.setPaddingEnd(this, 0);
    clearAnimation();
    setImageResource(R.drawable.ic_delivery_status_sent);
  }

  public void setDelivered() {
    state = State.DELIVERED;
    setVisibility(View.VISIBLE);
    ViewUtil.setPaddingStart(this, horizontalPadding);
    ViewUtil.setPaddingEnd(this, 0);
    clearAnimation();
    setImageResource(R.drawable.ic_delivery_status_delivered);
  }

  public void setRead() {
    state = State.READ;
    setVisibility(View.VISIBLE);
    ViewUtil.setPaddingStart(this, horizontalPadding);
    ViewUtil.setPaddingEnd(this, 0);
    clearAnimation();
    setImageResource(R.drawable.ic_delivery_status_read);
  }

  public void setTint(int color) {
    setColorFilter(color);
  }

  private enum State {
    NONE(0),
    PENDING(1),
    SENT(2),
    DELIVERED(3),
    READ(4);

    final int code;

    State(int code) {
      this.code = code;
    }

    static State fromCode(int code) {
      for (State state : State.values()) {
        if (state.code == code) {
          return state;
        }
      }

      return NONE;
    }
  }
}
