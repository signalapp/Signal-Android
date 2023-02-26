package org.thoughtcrime.securesms.components;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.os.Build.VERSION_CODES;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;

public class AlertView extends LinearLayout {

  private static final String TAG = Log.tag(AlertView.class);

  private ImageView approvalIndicator;
  private ImageView failedIndicator;

  public AlertView(Context context) {
    this(context, null);
  }

  public AlertView(Context context, AttributeSet attrs) {
    super(context, attrs);
    initialize(attrs);
  }

  public AlertView(final Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    initialize(attrs);
  }

  private void initialize(AttributeSet attrs) {
    inflate(getContext(), R.layout.alert_view, this);

    approvalIndicator = findViewById(R.id.pending_approval_indicator);
    failedIndicator   = findViewById(R.id.sms_failed_indicator);

    if (attrs != null) {
      TypedArray typedArray = getContext().getTheme().obtainStyledAttributes(attrs, R.styleable.AlertView, 0, 0);
      boolean useSmallIcon = typedArray.getBoolean(R.styleable.AlertView_useSmallIcon, false);
      typedArray.recycle();

      if (useSmallIcon) {
        int size = getResources().getDimensionPixelOffset(R.dimen.alertview_small_icon_size);
        failedIndicator.getLayoutParams().width  = size;
        failedIndicator.getLayoutParams().height = size;
        requestLayout();
      }
    }
  }

  public void setNone() {
    this.setVisibility(View.GONE);
  }

  public void setPendingApproval() {
    this.setVisibility(View.VISIBLE);
    approvalIndicator.setVisibility(View.VISIBLE);
    failedIndicator.setVisibility(View.GONE);
  }

  public void setFailed() {
    this.setVisibility(View.VISIBLE);
    approvalIndicator.setVisibility(View.GONE);
    failedIndicator.setVisibility(View.VISIBLE);
  }

  public void setRateLimited() {
    this.setVisibility(View.VISIBLE);
    approvalIndicator.setVisibility(View.VISIBLE);
    failedIndicator.setVisibility(View.GONE);
  }
}
