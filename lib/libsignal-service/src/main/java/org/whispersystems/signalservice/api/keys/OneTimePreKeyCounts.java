/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.keys;

import com.fasterxml.jackson.annotation.JsonProperty;

public class OneTimePreKeyCounts {

  @JsonProperty("count")
  private int ecCount;

  @JsonProperty("pqCount")
  private int kyberCount;

  public OneTimePreKeyCounts() {}

  public OneTimePreKeyCounts(int ecCount, int kyberCount) {
    this.ecCount    = ecCount;
    this.kyberCount = kyberCount;
  }

  public int getEcCount() {
    return ecCount;
  }

  public int getKyberCount() {
    return kyberCount;
  }
}
