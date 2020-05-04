package org.whispersystems.signalservice.internal.registrationpin;

public final class PinValidityChecker {

  public static boolean valid(String pin) {
    pin = pin.trim();

    if (pin.isEmpty()) {
      return false;
    }

    if (PinString.allNumeric(pin)) {
      pin = PinString.toArabic(pin);

      return !sequential(pin) &&
             !sequential(reverse(pin)) &&
             !allTheSame(pin);
    } else {
      return true;
    }
  }

  private static String reverse(String string) {
    char[] chars = string.toCharArray();

    for (int i = 0; i < chars.length / 2; i++) {
      char temp = chars[i];
      chars[i] = chars[chars.length - i - 1];
      chars[chars.length - i - 1] = temp;
    }

    return new String(chars);
  }

  private static boolean sequential(String pin) {
    int length = pin.length();

    if (length == 0) {
      return false;
    }

    char c = pin.charAt(0);

    for (int i = 1; i < length; i++) {
      char n = pin.charAt(i);
      if (n != c + 1) {
        return false;
      }
      c = n;
    }

    return true;
  }

  private static boolean allTheSame(String pin) {
    int length = pin.length();

    if (length == 0) {
      return false;
    }

    char c = pin.charAt(0);

    for (int i = 1; i < length; i++) {
      char n = pin.charAt(i);
      if (n != c) {
        return false;
      }
    }

    return true;
  }
}
