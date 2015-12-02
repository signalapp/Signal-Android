package org.thoughtcrime.securesms.components;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.os.Build.VERSION_CODES;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import org.thoughtcrime.securesms.R;

public class AlertView extends LinearLayout {

  private static final String TAG = AlertView.class.getSimpleName();

  private ImageView approvalIndicator;
  private ImageView failedIndicator;

  public AlertView(Context context) {
    this(context, null);
  }

  public AlertView(Context context, AttributeSet attrs) {
    super(context, attrs);
    initialize(attrs);
  }

  @TargetApi(VERSION_CODES.HONEYCOMB)
  public AlertView(final Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    initialize(attrs);
  }

  private void initialize(AttributeSet attrs) {
    inflate(getContext(), R.layout.alert_view, this);

    approvalIndicator = (ImageView) findViewById(R.id.pending_approval_indicator);
    failedIndicator   = (ImageView) findViewById(R.id.sms_failed_indicator);

    if (attrs != null) {
      TypedArray typedArray = getContext().getTheme().obtainStyledAttributes(attrs, R.styleable.AlertView, 0, 0);
      boolean useSmallIcon = typedArray.getBoolean(R.styleable.AlertView_useSmallIcon, false);
      typedArray.recycle();

      if (useSmallIcon) {
        failedIndicator.setImageDrawable(ContextCompat.getDrawable(getContext(), R.drawable.ic_error_red_18dp));
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
}
