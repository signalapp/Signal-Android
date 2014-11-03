package org.whispersystems.textsecure.api.messages;

import org.whispersystems.libaxolotl.util.guava.Optional;

public class TextSecureAttachmentPointer extends TextSecureAttachment {

  private final long             id;
  private final byte[]           key;
  private final Optional<String> relay;

  public TextSecureAttachmentPointer(long id, String contentType, byte[] key, String relay) {
    super(contentType);
    this.id    = id;
    this.key   = key;
    this.relay = Optional.fromNullable(relay);
  }

  public long getId() {
    return id;
  }

  public byte[] getKey() {
    return key;
  }

  @Override
  public boolean isStream() {
    return false;
  }

  @Override
  public boolean isPointer() {
    return true;
  }

  public Optional<String> getRelay() {
    return relay;
  }
}
