package org.thoughtcrime.securesms.components.emoji;

import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Emoji {

  private final List<String> variations;
  private final List<String> rawVariations;

  public Emoji(String... variations) {
    this(Arrays.asList(variations), Collections.emptyList());
  }

  public Emoji(List<String> variations) {
    this(variations, Collections.emptyList());
  }

  public Emoji(List<String> variations, List<String> rawVariations) {
    this.variations = variations;
    this.rawVariations = rawVariations;
  }

  public String getValue() {
    return variations.get(0);
  }

  public List<String> getVariations() {
    return variations;
  }

  public boolean hasMultipleVariations() {
    return variations.size() > 1;
  }

  public @Nullable String getRawVariation(int variationIndex) {
    if (rawVariations != null && variationIndex >= 0 && variationIndex < rawVariations.size()) {
      return rawVariations.get(variationIndex);
    }
    return null;
  }
}
