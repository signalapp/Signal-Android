/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.signal.libsignal.net.MismatchedDeviceException;

import java.util.ArrayList;
import java.util.List;

public class StaleDevices {

  @JsonProperty
  private List<Integer> staleDevices;

  public List<Integer> getStaleDevices() {
    return staleDevices;
  }

  public static StaleDevices fromLibSignal(MismatchedDeviceException.Entry entry) {
    StaleDevices  result = new StaleDevices();
    int[]         stale  = entry.getStaleDevices();
    List<Integer> list   = new ArrayList<>(stale.length);
    for (int value : stale) {
      list.add(value);
    }
    result.staleDevices = list;
    return result;
  }
}
