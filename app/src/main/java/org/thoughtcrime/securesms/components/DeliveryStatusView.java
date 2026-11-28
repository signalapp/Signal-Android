package org.thoughtcrime.securesms.components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.view.animation.Transformation;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.AppCompatImageView;

import org.signal.core.util.DimensionUnit;
import org.thoughtcrime.securesms.R;
import org.signal.core.ui.util.ViewUtil;

/**
 * View responsible for displaying the delivery status (NONE, PENDING, SENT, DELIVERED, READ) of a given outgoing message.
 * <p>
 * This view manipulates its start / end padding so that every icon is end-aligned within the width of the widest icon,
 * which keeps the footer from shifting as the status advances. It also performs a rotation animation on itself in the
 * pending mode. Thus, users should be aware that padding values set in XML will be overwritten.
 * <p>
 * If you need to control the horizontal spacing of this view, utilize margins instead.
 */
public class DeliveryStatusView extends AppCompatImageView {

  private static final String STATE_KEY = "DeliveryStatusView.STATE";
  private static final String ROOT_KEY  = "DeliveryStatusView.ROOT";

  private static final long CROSSFADE_DURATION = 100;

  private final int widePadding   = (int) DimensionUnit.DP.toPixels(2);
  private final int narrowPadding = (int) DimensionUnit.DP.toPixels(8);

  private RotateAnimation rotationAnimation;

  private State state = State.NONE;

  private boolean       animateStateChanges;
  private Drawable      fadingDrawable;
  private final Matrix  fadingMatrix = new Matrix();
  private float         crossfadeFraction;
  private ValueAnimator crossfadeAnimator;

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
      setTint(typedArray.getColor(R.styleable.DeliveryStatusView_iconColor, getResources().getColor(org.signal.core.ui.R.color.core_white)));
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
    if (state == State.PENDING) {
      startRotation();
    }
  }

  private void startRotation() {
    int   w           = getWidth();
    int   h           = getHeight();
    float pivotXValue = getPaddingLeft() + (w - getPaddingLeft() - getPaddingRight()) / 2f;
    float pivotYValue = getPaddingTop() + (h - getPaddingTop() - getPaddingBottom()) / 2f;

    rotationAnimation = new RotateAnimation(0, 360f,
                                            Animation.ABSOLUTE, pivotXValue,
                                            Animation.ABSOLUTE, pivotYValue);

    rotationAnimation.setInterpolator(new LinearInterpolator());
    rotationAnimation.setDuration(1500);
    rotationAnimation.setRepeatCount(Animation.INFINITE);

    startAnimation(rotationAnimation);
  }

  @Override
  public void clearAnimation() {
    super.clearAnimation();
    rotationAnimation = null;
  }

  @Override
  protected void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    endCrossfade();
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);

    if (fadingDrawable != null) {
      int saveCount = canvas.save();
      canvas.concat(fadingMatrix);
      fadingDrawable.setAlpha(Math.round(255 * (1f - crossfadeFraction)));
      fadingDrawable.draw(canvas);
      canvas.restoreToCount(saveCount);
    }
  }

  /**
   * When enabled, changes into sent, delivered, or read crossfade instead of swapping instantly. Callers should
   * only enable this when the view is being rebound to the same message.
   */
  public void setAnimateStateChanges(boolean animate) {
    animateStateChanges = animate;
    if (!animate) {
      endCrossfade();
    }
  }

  public void setNone() {
    endCrossfade();
    state = State.NONE;
    clearAnimation();
    setVisibility(View.GONE);
    updateContentDescription();
  }

  public boolean isPending() {
    return state == State.PENDING;
  }

  public void setPending() {
    boolean wasPending = state == State.PENDING;

    endCrossfade();
    state = State.PENDING;
    setVisibility(View.VISIBLE);
    ViewUtil.setPaddingStart(this, narrowPadding);
    ViewUtil.setPaddingEnd(this, 0);
    setImageResource(R.drawable.symbol_messagestatus_sending_24);
    updateContentDescription();

    if (!wasPending && getWidth() > 0) {
      startRotation();
    }
  }

  public void setSent() {
    setSettledState(State.SENT, narrowPadding, R.drawable.symbol_messagestatus_sent_24);
  }

  public void setDelivered() {
    setSettledState(State.DELIVERED, widePadding, R.drawable.symbol_messagestatus_delivered_24);
  }

  public void setRead() {
    setSettledState(State.READ, widePadding, R.drawable.symbol_messagestatus_read_24);
  }

  private void setSettledState(State newState, int paddingStart, @DrawableRes int icon) {
    boolean crossfade = animateStateChanges &&
                        newState != state &&
                        state != State.NONE &&
                        isLaidOut() &&
                        getDrawable() != null;

    if (newState != state) {
      endCrossfade();
    }

    if (crossfade) {
      fadingDrawable    = getDrawable().mutate();
      fadingMatrix.set(getImageMatrix());
      fadingMatrix.postTranslate(getPaddingLeft(), getPaddingTop());

      if (rotationAnimation != null) {
        Transformation rotation = new Transformation();
        rotationAnimation.getTransformation(AnimationUtils.currentAnimationTimeMillis(), rotation);
        fadingMatrix.postConcat(rotation.getMatrix());
      }
    }

    state = newState;
    setVisibility(View.VISIBLE);
    ViewUtil.setPaddingStart(this, paddingStart);
    ViewUtil.setPaddingEnd(this, 0);
    clearAnimation();
    setImageResource(icon);
    updateContentDescription();

    if (crossfade) {
      startCrossfade();
    }
  }

  private void startCrossfade() {
    crossfadeFraction = 0f;
    setImageAlpha(0);

    crossfadeAnimator = ValueAnimator.ofFloat(0f, 1f);
    crossfadeAnimator.setDuration(CROSSFADE_DURATION);
    crossfadeAnimator.addUpdateListener(animation -> {
      crossfadeFraction = (float) animation.getAnimatedValue();
      setImageAlpha(Math.round(255 * crossfadeFraction));
    });
    crossfadeAnimator.addListener(new AnimatorListenerAdapter() {
      @Override
      public void onAnimationEnd(Animator animation) {
        endCrossfade();
      }
    });
    crossfadeAnimator.start();
  }

  private void endCrossfade() {
    if (crossfadeAnimator != null) {
      crossfadeAnimator.removeAllUpdateListeners();
      crossfadeAnimator.removeAllListeners();
      crossfadeAnimator.cancel();
      crossfadeAnimator = null;
    }

    if (fadingDrawable != null) {
      fadingDrawable = null;
      setImageAlpha(255);
    }
  }

  public void setTint(int color) {
    setColorFilter(color, PorterDuff.Mode.SRC_IN);
  }

  private void updateContentDescription() {
    if (state.contentDescription == -1) {
      setContentDescription(null);
    } else {
      setContentDescription(getContext().getString(state.contentDescription));
    }
  }

  private enum State {
    NONE(0, -1),
    PENDING(1, R.string.message_details_recipient_header__pending_send),
    SENT(2, R.string.message_details_header_sent),
    DELIVERED(3, R.string.conversation_item_sent__delivered_description),
    READ(4, R.string.conversation_item_sent__message_read);

    final int code;

    @StringRes
    final int contentDescription;

    State(int code, @StringRes int contentDescription) {
      this.code               = code;
      this.contentDescription = contentDescription;
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
