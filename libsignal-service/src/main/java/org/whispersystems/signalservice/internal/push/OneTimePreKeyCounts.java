/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.internal.push;

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
