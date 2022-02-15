package org.whispersystems.signalservice.api.push;

import java.util.UUID;

/**
 * A wrapper around a UUID that represents an identifier for an account. Today, that is either an {@link ACI} or a {@link PNI}.
 */
public abstract class AccountIdentifier {

  protected final UUID uuid;

  protected AccountIdentifier(UUID uuid) {
    this.uuid = uuid;
  }

  public UUID uuid() {
    return uuid;
  }

  @Override
  public String toString() {
    return uuid.toString();
  }
}
