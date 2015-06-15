package org.whispersystems.textsecure.api.messages.multidevice;

import org.whispersystems.libaxolotl.util.guava.Optional;
import org.whispersystems.textsecure.api.messages.TextSecureDataMessage;

public class SentTranscriptMessage {

  private final Optional<String>      destination;
  private final long                  timestamp;
  private final TextSecureDataMessage message;

  public SentTranscriptMessage(String destination, long timestamp, TextSecureDataMessage message) {
    this.destination = Optional.of(destination);
    this.timestamp   = timestamp;
    this.message     = message;
  }

  public SentTranscriptMessage(long timestamp, TextSecureDataMessage message) {
    this.destination = Optional.absent();
    this.timestamp   = timestamp;
    this.message     = message;
  }

  public Optional<String> getDestination() {
    return destination;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public TextSecureDataMessage getMessage() {
    return message;
  }
}
