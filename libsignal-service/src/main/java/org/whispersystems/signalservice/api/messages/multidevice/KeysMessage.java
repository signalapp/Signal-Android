package org.whispersystems.signalservice.api.messages.multidevice;


import org.whispersystems.signalservice.api.kbs.MasterKey;
import org.whispersystems.signalservice.api.storage.StorageKey;

import java.util.Optional;

public class KeysMessage {

  private final Optional<StorageKey> storageService;
  private final Optional<MasterKey>  master;

  public KeysMessage(Optional<StorageKey> storageService, Optional<MasterKey> master) {
    this.storageService = storageService;
    this.master         = master;
  }

  public Optional<StorageKey> getStorageService() {
    return storageService;
  }

  public Optional<MasterKey> getMaster() {
    return master;
  }
}
