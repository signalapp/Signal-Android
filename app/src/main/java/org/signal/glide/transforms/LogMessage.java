package org.signal.glide.transforms;

public record LogMessage(String value) {
  public LogMessage {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("LogMessage cannot be null or blank");
    }
  }
}
