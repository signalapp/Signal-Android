package org.thoughtcrime.securesms.util;

import android.view.View;

public class StatusManager {

  private final View pendingIndicator;
  private final View sentIndicator;
  private final View deliveredIndicator;

  private final View failedIndicator;
  private final View approvalIndicator;


  public StatusManager(View pendingIndicator, View sentIndicator, View deliveredIndicator,
                       View failedIndicator, View approvalIndicator) {
    this.pendingIndicator = pendingIndicator;
    this.sentIndicator = sentIndicator;
    this.deliveredIndicator = deliveredIndicator;
    this.failedIndicator = failedIndicator;
    this.approvalIndicator = approvalIndicator;
  }

  public void hideAll() {
    pendingIndicator.setVisibility(View.GONE);
    sentIndicator.setVisibility(View.GONE);
    deliveredIndicator.setVisibility(View.GONE);
    approvalIndicator.setVisibility(View.GONE);
    failedIndicator.setVisibility(View.GONE);
  }

  public void displayFailed() {
    pendingIndicator.setVisibility(View.GONE);
    sentIndicator.setVisibility(View.GONE);
    deliveredIndicator.setVisibility(View.GONE);
    approvalIndicator.setVisibility(View.GONE);

    failedIndicator.setVisibility(View.VISIBLE);
  }

  public void displayPendingApproval() {
    pendingIndicator.setVisibility(View.GONE);
    sentIndicator.setVisibility(View.GONE);
    deliveredIndicator.setVisibility(View.GONE);
    failedIndicator.setVisibility(View.GONE);

    approvalIndicator.setVisibility(View.VISIBLE);
  }

  public void displayPending() {
    sentIndicator.setVisibility(View.GONE);
    deliveredIndicator.setVisibility(View.GONE);
    failedIndicator.setVisibility(View.GONE);
    approvalIndicator.setVisibility(View.GONE);

    pendingIndicator.setVisibility(View.VISIBLE);
  }

  public void displaySent() {
    pendingIndicator.setVisibility(View.GONE);
    deliveredIndicator.setVisibility(View.GONE);
    failedIndicator.setVisibility(View.GONE);
    approvalIndicator.setVisibility(View.GONE);

    sentIndicator.setVisibility(View.VISIBLE);
  }

  public void displayDelivered() {
    pendingIndicator.setVisibility(View.GONE);
    failedIndicator.setVisibility(View.GONE);
    approvalIndicator.setVisibility(View.GONE);
    sentIndicator.setVisibility(View.GONE);

    deliveredIndicator.setVisibility(View.VISIBLE);
  }
}
