package org.whispersystems.libaxolotl.util;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public class Helper {

  public static int getRandomSequence(int max) {
    try {
      return SecureRandom.getInstance("SHA1PRNG").nextInt(max);
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }

}
