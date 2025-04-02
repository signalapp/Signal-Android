/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.donations;

import com.fasterxml.jackson.annotation.JsonProperty;

class PayPalCreatePaymentMethodPayload {
  @JsonProperty
  private String returnUrl;

  @JsonProperty
  private String cancelUrl;

  PayPalCreatePaymentMethodPayload(String returnUrl, String cancelUrl) {
    this.returnUrl = returnUrl;
    this.cancelUrl = cancelUrl;
  }
}
