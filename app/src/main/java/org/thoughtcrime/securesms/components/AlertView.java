package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;

import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.content.ContextCompat;

import org.thoughtcrime.securesms.R;

public class AlertView extends AppCompatImageView {

  public AlertView(Context context) {
    this(context, null);
  }

  public AlertView(Context context, AttributeSet attrs) {
    super(context, attrs);
    initialize();
  }

  public AlertView(final Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    initialize();
  }

  private void initialize() {
    setImageResource(R.drawable.symbol_error_circle_compact_16);
    setScaleType(ScaleType.FIT_CENTER);
  }

  public void setNone() {
    setVisibility(View.GONE);
  }

  public void setFailed() {
    setVisibility(View.VISIBLE);
    setColorFilter(ContextCompat.getColor(getContext(), R.color.signal_colorError));
    setContentDescription(getContext().getString(R.string.conversation_item_sent__send_failed_indicator_description));
  }

  public void setRateLimited() {
    setVisibility(View.VISIBLE);
    setColorFilter(ContextCompat.getColor(getContext(), R.color.signal_colorOnSurfaceVariant));
    setContentDescription(getContext().getString(R.string.conversation_item_sent__pending_approval_description));
  }
}
