package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.thoughtcrime.securesms.R;

import pl.tajchert.sample.DotsTextView;

public class DeliveryStatusView extends FrameLayout {

  private static final String TAG = DeliveryStatusView.class.getSimpleName();

  private final ViewGroup pendingIndicatorStub;
  private final ImageView sentIndicator;
  private final ImageView deliveredIndicator;

  public DeliveryStatusView(Context context) {
    this(context, null);
  }

  public DeliveryStatusView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public DeliveryStatusView(final Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);

    inflate(context, R.layout.delivery_status_view, this);

    this.deliveredIndicator   = (ImageView) findViewById(R.id.delivered_indicator);
    this.sentIndicator        = (ImageView) findViewById(R.id.sent_indicator);
    this.pendingIndicatorStub = (ViewGroup) findViewById(R.id.pending_indicator_stub);

    int iconColor = Color.GRAY;

    if (attrs != null) {
      TypedArray typedArray = context.getTheme().obtainStyledAttributes(attrs, R.styleable.DeliveryStatusView, 0, 0);
      iconColor = typedArray.getColor(R.styleable.DeliveryStatusView_iconColor, iconColor);
      typedArray.recycle();
    }

    deliveredIndicator.setColorFilter(iconColor, android.graphics.PorterDuff.Mode.MULTIPLY);
    sentIndicator.setColorFilter(iconColor, android.graphics.PorterDuff.Mode.MULTIPLY);

    if (Build.VERSION.SDK_INT >= 11) {
      inflate(context, R.layout.conversation_item_pending_v11, pendingIndicatorStub);
      DotsTextView pendingIndicator = (DotsTextView) findViewById(R.id.pending_indicator);
      pendingIndicator.setDotsColor(iconColor);
    } else {
      inflate(context, R.layout.conversation_item_pending, pendingIndicatorStub);
      TextView pendingIndicator = (TextView) findViewById(R.id.pending_indicator);
      pendingIndicator.setTextColor(iconColor);
    }
  }

  public void setNone() {
    this.setVisibility(View.GONE);
  }

  public void setPending() {
    this.setVisibility(View.VISIBLE);
    pendingIndicatorStub.setVisibility(View.VISIBLE);
    sentIndicator.setVisibility(View.GONE);
    deliveredIndicator.setVisibility(View.GONE);
  }

  public void setSent() {
    this.setVisibility(View.VISIBLE);
    pendingIndicatorStub.setVisibility(View.GONE);
    sentIndicator.setVisibility(View.VISIBLE);
    deliveredIndicator.setVisibility(View.GONE);
  }

  public void setDelivered() {
    this.setVisibility(View.VISIBLE);
    pendingIndicatorStub.setVisibility(View.GONE);
    sentIndicator.setVisibility(View.GONE);
    deliveredIndicator.setVisibility(View.VISIBLE);
  }
}
