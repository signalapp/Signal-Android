package org.thoughtcrime.securesms.components.emoji;

import java.util.Arrays;
import java.util.List;

public class Emoji {

  private final List<String> variations;

  public Emoji(String... variations) {
    this.variations = Arrays.asList(variations);
  }

  public String getValue() {
    return variations.get(0);
  }

  public List<String> getVariations() {
    return variations;
  }
}
