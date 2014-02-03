package org.whispersystems.textsecure.push;

import java.util.List;

public class MismatchedDevices {
  private List<Integer> missingDevices;

  private List<Integer> extraDevices;

  public List<Integer> getMissingDevices() {
    return missingDevices;
  }

  public List<Integer> getExtraDevices() {
    return extraDevices;
  }
}
