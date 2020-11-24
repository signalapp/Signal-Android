package org.whispersystems.signalservice.api.messages.multidevice;

import org.whispersystems.libsignal.util.guava.Optional;

public class StickerPackOperationMessage {

  private final Optional<byte[]> packId;
  private final Optional<byte[]> packKey;
  private final Optional<Type>   type;

  public StickerPackOperationMessage(byte[] packId, byte[] packKey, Type type) {
    this.packId  = Optional.fromNullable(packId);
    this.packKey = Optional.fromNullable(packKey);
    this.type    = Optional.fromNullable(type);
  }

  public Optional<byte[]> getPackId() {
    return packId;
  }

  public Optional<byte[]> getPackKey() {
    return packKey;
  }

  public Optional<Type> getType() {
    return type;
  }

  public enum Type {
    INSTALL, REMOVE
  }
}
