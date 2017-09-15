package org.thoughtcrime.securesms.components.emoji.parsing;

class VariationSelectors {
  private static final int VS_LOWER_BOUND = 0xFE00;
  private static final int VS_UPPER_BOUND = 0xFE0F;

  public static boolean match(char c) {
   return (VS_LOWER_BOUND <= c && c <= VS_UPPER_BOUND);
  }
}
