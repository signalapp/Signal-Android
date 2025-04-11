package org.signal.glide.transforms;

public record LogTag(String value) {
  public LogTag {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("LogTag cannot be null or blank");
    }
  }
}
