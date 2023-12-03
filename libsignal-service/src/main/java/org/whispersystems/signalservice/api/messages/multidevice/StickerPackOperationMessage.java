package org.whispersystems.signalservice.api.messages.multidevice;


import java.util.Optional;

public class StickerPackOperationMessage {

  private final Optional<byte[]> packId;
  private final Optional<byte[]> packKey;
  private final Optional<Type>   type;

  public StickerPackOperationMessage(byte[] packId, byte[] packKey, Type type) {
    this.packId  = Optional.ofNullable(packId);
    this.packKey = Optional.ofNullable(packKey);
    this.type    = Optional.ofNullable(type);
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
