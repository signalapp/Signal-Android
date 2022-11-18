package org.thoughtcrime.securesms.devicetransfer;

/**
 * The various steps involved in setting up a transfer connection. Each step has a
 * corresponding UI.
 */
public enum SetupStep {
  INITIAL(true, false),
  PERMISSIONS_CHECK(true, false),
  PERMISSIONS_DENIED(false, true),
  LOCATION_CHECK(true, false),
  LOCATION_DISABLED(false, true),
  WIFI_CHECK(true, false),
  WIFI_DISABLED(false, true),
  WIFI_DIRECT_CHECK(true, false),
  WIFI_DIRECT_UNAVAILABLE(false, true),
  START(true, false),
  SETTING_UP(true, false),
  WAITING(true, false),
  VERIFY(false, false),
  WAITING_FOR_OTHER_TO_VERIFY(false, false),
  CONNECTED(true, false),
  TROUBLESHOOTING(false, false),
  ERROR(false, true);

  private final boolean isProgress;
  private final boolean isError;

  SetupStep(boolean isProgress, boolean isError) {
    this.isProgress = isProgress;
    this.isError    = isError;
  }

  public boolean isProgress() {
    return isProgress;
  }

  public boolean isError() {
    return isError;
  }
}
