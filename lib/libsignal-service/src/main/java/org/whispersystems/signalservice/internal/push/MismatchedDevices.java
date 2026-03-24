/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.signal.libsignal.net.MismatchedDeviceException;

import java.util.ArrayList;
import java.util.List;

public class MismatchedDevices {
  @JsonProperty
  public List<Integer> missingDevices;

  @JsonProperty
  public List<Integer> extraDevices;

  public List<Integer> getMissingDevices() {
    return missingDevices;
  }

  public List<Integer> getExtraDevices() {
    return extraDevices;
  }

  public static MismatchedDevices fromLibSignal(MismatchedDeviceException.Entry entry) {
    MismatchedDevices result = new MismatchedDevices();

    result.missingDevices = toList(entry.getMissingDevices());
    result.extraDevices   = toList(entry.getExtraDevices());

    return result;
  }

  private static List<Integer> toList(int[] array) {
    List<Integer> list = new ArrayList<>(array.length);
    for (int value : array) {
      list.add(value);
    }
    return list;
  }
}
