package org.whispersystems.signalservice.api.payments;

public final class PaymentsConstants {

  private PaymentsConstants() {}

  public static final int PAYMENTS_ENTROPY_LENGTH = 32;
  public static final int MNEMONIC_LENGTH         = Math.round(PAYMENTS_ENTROPY_LENGTH * 0.75f);
  public static final int SHORT_FRACTION_LENGTH   = 4;

}
