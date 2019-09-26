package org.whispersystems.signalservice.api.messages.multidevice;


import org.whispersystems.libsignal.util.guava.Optional;

public class KeysMessage {

  private final Optional<byte[]> storageService;

  public KeysMessage(Optional<byte[]> storageService) {
    this.storageService = storageService;
  }

  public Optional<byte[]> getStorageService() {
    return storageService;
  }
}
