package org.whispersystems.textsecure.api.messages;

import org.whispersystems.libaxolotl.util.guava.Optional;
import org.whispersystems.textsecure.api.messages.multidevice.TextSecureSyncMessage;

public class TextSecureContent {

  private final Optional<TextSecureDataMessage> message;
  private final Optional<TextSecureSyncMessage> synchronizeMessage;

  public TextSecureContent() {
    this.message            = Optional.absent();
    this.synchronizeMessage = Optional.absent();
  }

  public TextSecureContent(TextSecureDataMessage message) {
    this.message = Optional.fromNullable(message);
    this.synchronizeMessage = Optional.absent();
  }

  public TextSecureContent(TextSecureSyncMessage synchronizeMessage) {
    this.message            = Optional.absent();
    this.synchronizeMessage = Optional.fromNullable(synchronizeMessage);
  }

  public Optional<TextSecureDataMessage> getDataMessage() {
    return message;
  }

  public Optional<TextSecureSyncMessage> getSyncMessage() {
    return synchronizeMessage;
  }
}
