package org.whispersystems.signalservice.api.crypto;


import org.whispersystems.libsignal.util.guava.Optional;

public class UnidentifiedAccessPair {

  private final Optional<UnidentifiedAccess> targetUnidentifiedAccess;
  private final Optional<UnidentifiedAccess> selfUnidentifiedAccess;

  public UnidentifiedAccessPair(UnidentifiedAccess targetUnidentifiedAccess, UnidentifiedAccess selfUnidentifiedAccess) {
    this.targetUnidentifiedAccess = Optional.of(targetUnidentifiedAccess);
    this.selfUnidentifiedAccess   = Optional.of(selfUnidentifiedAccess);
  }

  public Optional<UnidentifiedAccess> getTargetUnidentifiedAccess() {
    return targetUnidentifiedAccess;
  }

  public Optional<UnidentifiedAccess> getSelfUnidentifiedAccess() {
    return selfUnidentifiedAccess;
  }
}
