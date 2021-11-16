package org.whispersystems.signalservice.api.push;

import java.util.UUID;

/**
 * A PNI is a "Phone Number Identity". They're just UUIDs, but given multiple different things could be UUIDs, this wrapper exists to give us type safety around
 * this *specific type* of UUID.
 */
public final class PNI extends AccountIdentifier {

  public static PNI from(UUID uuid) {
    return new PNI(uuid);
  }

  private PNI(UUID uuid) {
    super(uuid);
  }

  @Override
  public int hashCode() {
    return uuid.hashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other instanceof PNI) {
      return uuid.equals(((PNI) other).uuid);
    } else {
      return false;
    }
  }
}
