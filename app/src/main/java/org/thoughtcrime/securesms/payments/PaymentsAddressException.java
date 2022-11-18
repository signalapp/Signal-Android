package org.thoughtcrime.securesms.payments;

import androidx.annotation.NonNull;

public final class PaymentsAddressException extends Exception {

  private final Code code;

  public PaymentsAddressException(@NonNull Code code) {
    super(code.message);
    this.code = code;
  }

  public @NonNull Code getCode() {
    return code;
  }

  public enum Code {
    NO_PROFILE_KEY("No profile key available"),
    NOT_ENABLED("Payments not enabled"),
    COULD_NOT_DECRYPT("Payment address could not be decrypted"),
    INVALID_ADDRESS("Invalid MobileCoin address on payments address proto"),
    INVALID_ADDRESS_SIGNATURE("Invalid MobileCoin address signature on payments address proto"),
    NO_ADDRESS("No MobileCoin address on payments address proto");

    private final String message;

    Code(@NonNull String message) {
      this.message = message;
    }
  }
}
