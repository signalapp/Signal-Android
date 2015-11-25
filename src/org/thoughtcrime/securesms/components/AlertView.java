package org.thoughtcrime.securesms.components;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build.VERSION_CODES;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;

import org.thoughtcrime.securesms.R;

public class AlertView extends LinearLayout {

  private static final String TAG = AlertView.class.getSimpleName();

  private View approvalIndicator;
  private View failedIndicator;

  public AlertView(Context context) {
    this(context, null);
  }

  public AlertView(Context context, AttributeSet attrs) {
    super(context, attrs);
    initialize();
  }

  @TargetApi(VERSION_CODES.HONEYCOMB)
  public AlertView(final Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    initialize();
  }

  private void initialize() {
    inflate(getContext(), R.layout.alert_view, this);

    approvalIndicator = findViewById(R.id.pending_approval_indicator);
    failedIndicator   = findViewById(R.id.sms_failed_indicator);
  }

  public void setNone() {
    this.setVisibility(View.GONE);
  }

  public void setPendingApproval() {
    this             .setVisibility(View.VISIBLE);
    approvalIndicator.setVisibility(View.VISIBLE);
    failedIndicator  .setVisibility(View.GONE);
  }

  public void setFailed() {
    this             .setVisibility(View.VISIBLE);
    approvalIndicator.setVisibility(View.GONE);
    failedIndicator  .setVisibility(View.VISIBLE);
  }
}
