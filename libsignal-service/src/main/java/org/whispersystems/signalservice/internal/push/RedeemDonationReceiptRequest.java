package org.whispersystems.signalservice.internal.push;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.signal.libsignal.zkgroup.receipts.ReceiptCredentialPresentation;

/**
 * POST /v1/donation/redeem-receipt
 *
 * Request object for redeeming a receipt from a donation transaction.
 */
class RedeemDonationReceiptRequest {

  private final String  receiptCredentialPresentation;
  private final boolean visible;
  private final boolean primary;

  /**
   * @param receiptCredentialPresentation base64-encoded no-newlines standard-character-set with-padding of the bytes of a {@link ReceiptCredentialPresentation} object
   * @param visible boolean indicating if the new badge should be visible or not on the profile
   * @param primary boolean indicating if the new badge should be primary or not on the profile; is always treated as false if `visible` is false
   */
  @JsonCreator RedeemDonationReceiptRequest(
      @JsonProperty("receiptCredentialPresentation") String receiptCredentialPresentation,
      @JsonProperty("visible") boolean visible,
      @JsonProperty("primary") boolean primary) {

    this.receiptCredentialPresentation = receiptCredentialPresentation;
    this.visible = visible;
    this.primary = primary;
  }

  public String getReceiptCredentialPresentation() {
    return receiptCredentialPresentation;
  }

  public boolean isVisible() {
    return visible;
  }

  public boolean isPrimary() {
    return primary;
  }
}