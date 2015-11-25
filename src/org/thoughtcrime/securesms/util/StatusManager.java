package org.thoughtcrime.securesms.util;

import android.view.View;

import org.thoughtcrime.securesms.components.DeliveryStatusView;

public class StatusManager {

  private final DeliveryStatusView deliveryStatus;
  private final View               failedIndicator;
  private final View               approvalIndicator;

  public StatusManager(DeliveryStatusView deliveryStatus, View failedIndicator,
                       View approvalIndicator) {
    this.deliveryStatus    = deliveryStatus;
    this.failedIndicator   = failedIndicator;
    this.approvalIndicator = approvalIndicator;
  }

  public void hideAll() {
    deliveryStatus   .setNone();
    approvalIndicator.setVisibility(View.GONE);
    failedIndicator  .setVisibility(View.GONE);
  }

  public void displayFailed() {
    deliveryStatus   .setNone();
    approvalIndicator.setVisibility(View.GONE);
    failedIndicator  .setVisibility(View.VISIBLE);
  }

  public void displayPendingApproval() {
    deliveryStatus   .setNone();
    approvalIndicator.setVisibility(View.VISIBLE);
    failedIndicator  .setVisibility(View.GONE);
  }

  public void displayPending() {
    deliveryStatus   .setPending();
    approvalIndicator.setVisibility(View.GONE);
    failedIndicator  .setVisibility(View.GONE);
  }

  public void displaySent() {
    deliveryStatus   .setSent();
    approvalIndicator.setVisibility(View.GONE);
    failedIndicator  .setVisibility(View.GONE);
  }

  public void displayDelivered() {
    deliveryStatus   .setDelivered();
    approvalIndicator.setVisibility(View.GONE);
    failedIndicator  .setVisibility(View.GONE);
  }
}
