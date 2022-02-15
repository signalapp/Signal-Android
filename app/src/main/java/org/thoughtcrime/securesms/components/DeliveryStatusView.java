package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;

public class DeliveryStatusView extends FrameLayout {

  private static final String TAG = Log.tag(DeliveryStatusView.class);

  private final RotateAnimation rotationAnimation;
  private final ImageView       pendingIndicator;
  private final ImageView       sentIndicator;
  private final ImageView       deliveredIndicator;
  private final ImageView       readIndicator;

  public DeliveryStatusView(Context context) {
    this(context, null);
  }

  public DeliveryStatusView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public DeliveryStatusView(final Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);

    inflate(context, R.layout.delivery_status_view, this);

    this.deliveredIndicator = findViewById(R.id.delivered_indicator);
    this.sentIndicator      = findViewById(R.id.sent_indicator);
    this.pendingIndicator   = findViewById(R.id.pending_indicator);
    this.readIndicator      = findViewById(R.id.read_indicator);

    rotationAnimation = new RotateAnimation(0, 360f,
                                            Animation.RELATIVE_TO_SELF, 0.5f,
                                            Animation.RELATIVE_TO_SELF, 0.5f);
    rotationAnimation.setInterpolator(new LinearInterpolator());
    rotationAnimation.setDuration(1500);
    rotationAnimation.setRepeatCount(Animation.INFINITE);

    if (attrs != null) {
      TypedArray typedArray = context.getTheme().obtainStyledAttributes(attrs, R.styleable.DeliveryStatusView, 0, 0);
      setTint(typedArray.getColor(R.styleable.DeliveryStatusView_iconColor, getResources().getColor(R.color.core_white)));
      typedArray.recycle();
    }
  }

  public void setNone() {
    this.setVisibility(View.GONE);
  }

  public boolean isPending() {
    return pendingIndicator.getVisibility() == View.VISIBLE;
  }

  public void setPending() {
    this.setVisibility(View.VISIBLE);
    pendingIndicator.setVisibility(View.VISIBLE);
    pendingIndicator.startAnimation(rotationAnimation);
    sentIndicator.setVisibility(View.GONE);
    deliveredIndicator.setVisibility(View.GONE);
    readIndicator.setVisibility(View.GONE);
  }

  public void setSent() {
    this.setVisibility(View.VISIBLE);
    pendingIndicator.setVisibility(View.GONE);
    pendingIndicator.clearAnimation();
    sentIndicator.setVisibility(View.VISIBLE);
    deliveredIndicator.setVisibility(View.GONE);
    readIndicator.setVisibility(View.GONE);
  }

  public void setDelivered() {
    this.setVisibility(View.VISIBLE);
    pendingIndicator.setVisibility(View.GONE);
    pendingIndicator.clearAnimation();
    sentIndicator.setVisibility(View.GONE);
    deliveredIndicator.setVisibility(View.VISIBLE);
    readIndicator.setVisibility(View.GONE);
  }

  public void setRead() {
    this.setVisibility(View.VISIBLE);
    pendingIndicator.setVisibility(View.GONE);
    pendingIndicator.clearAnimation();
    sentIndicator.setVisibility(View.GONE);
    deliveredIndicator.setVisibility(View.GONE);
    readIndicator.setVisibility(View.VISIBLE);
  }

  public void setTint(int color) {
    pendingIndicator.setColorFilter(color);
    deliveredIndicator.setColorFilter(color);
    sentIndicator.setColorFilter(color);
    readIndicator.setColorFilter(color);
  }
}
