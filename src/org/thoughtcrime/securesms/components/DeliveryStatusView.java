package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.os.Build;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.thoughtcrime.securesms.R;

import pl.tajchert.sample.DotsTextView;

public class DeliveryStatusView extends FrameLayout {

  private static final String TAG = DeliveryStatusView.class.getSimpleName();

  public DeliveryStatusView(Context context) {
    this(context, null);
  }

  public DeliveryStatusView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public DeliveryStatusView(final Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);

    inflate(context, R.layout.delivery_status_view, this);

    ImageView deliveredIndicator   = (ImageView) findViewById(R.id.delivered_indicator);
    ImageView sentIndicator        = (ImageView) findViewById(R.id.sent_indicator);
    ViewGroup pendingIndicatorStub = (ViewGroup) findViewById(R.id.pending_indicator_stub);

    int iconColor = Color.GRAY;

    if (attrs != null) {
      TypedArray typedArray = context.getTheme().obtainStyledAttributes(attrs, R.styleable.DeliveryStatusView, 0, 0);
      iconColor = typedArray.getColor(0, Color.GRAY);
      typedArray.recycle();

      deliveredIndicator.setColorFilter(iconColor, android.graphics.PorterDuff.Mode.MULTIPLY);
      sentIndicator.     setColorFilter(iconColor, android.graphics.PorterDuff.Mode.MULTIPLY);
    }

    if (pendingIndicatorStub != null) {
      LayoutInflater inflater = LayoutInflater.from(context);
      if (Build.VERSION.SDK_INT >= 11) {
        inflater.inflate(R.layout.conversation_item_pending_v11, pendingIndicatorStub, true);
        DotsTextView pendingIndicator = (DotsTextView) findViewById(R.id.pending_indicator);
        pendingIndicator.setDotsColor(iconColor);
      } else {
        inflater.inflate(R.layout.conversation_item_pending, pendingIndicatorStub, true);
        TextView pendingIndicator = (TextView) findViewById(R.id.pending_indicator);
        pendingIndicator.setTextColor(iconColor);
      }
    }
  }
}
