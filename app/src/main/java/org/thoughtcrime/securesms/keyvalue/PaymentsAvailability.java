package org.thoughtcrime.securesms.keyvalue;

public enum PaymentsAvailability {
  NOT_IN_REGION(false, false),
  DISABLED_REMOTELY(false, false),
  REGISTRATION_AVAILABLE(false, true),
  WITHDRAW_ONLY(true, true),
  WITHDRAW_AND_SEND(true, true);

  private final boolean showPaymentsMenu;
  private final boolean isEnabled;

  PaymentsAvailability(boolean isEnabled, boolean showPaymentsMenu) {
    this.showPaymentsMenu = showPaymentsMenu;
    this.isEnabled        = isEnabled;
  }

  public boolean isEnabled() {
    return isEnabled;
  }

  public boolean showPaymentsMenu() {
    return showPaymentsMenu;
  }

  public boolean isSendAllowed() {
    return this == WITHDRAW_AND_SEND;
  }

  public boolean canRegister() {
    return this == REGISTRATION_AVAILABLE;
  }
}
