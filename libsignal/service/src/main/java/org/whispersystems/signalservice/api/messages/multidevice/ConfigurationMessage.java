package org.whispersystems.signalservice.api.messages.multidevice;


import org.whispersystems.libsignal.util.guava.Optional;

public class ConfigurationMessage {

  private final Optional<Boolean> readReceipts;
  private final Optional<Boolean> unidentifiedDeliveryIndicators;
  private final Optional<Boolean> typingIndicators;
  private final Optional<Boolean> linkPreviews;

  public ConfigurationMessage(Optional<Boolean> readReceipts,
                              Optional<Boolean> unidentifiedDeliveryIndicators,
                              Optional<Boolean> typingIndicators,
                              Optional<Boolean> linkPreviews)
  {
    this.readReceipts                   = readReceipts;
    this.unidentifiedDeliveryIndicators = unidentifiedDeliveryIndicators;
    this.typingIndicators               = typingIndicators;
    this.linkPreviews                   = linkPreviews;
  }

  public Optional<Boolean> getReadReceipts() {
    return readReceipts;
  }

  public Optional<Boolean> getUnidentifiedDeliveryIndicators() {
    return unidentifiedDeliveryIndicators;
  }

  public Optional<Boolean> getTypingIndicators() {
    return typingIndicators;
  }

  public Optional<Boolean> getLinkPreviews() {
    return linkPreviews;
  }
}
