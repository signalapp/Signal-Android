package org.whispersystems.signalservice.api.push;

import com.google.protobuf.ByteString;

import org.whispersystems.signalservice.api.push.ServiceId.ACI;
import org.whispersystems.signalservice.api.push.ServiceId.PNI;

import java.util.Objects;
import java.util.UUID;

/**
 * Helper for dealing with [ServiceId] matching when you only care that either of your
 * service ids match but don't care which one.
 */
public final class ServiceIds {

  private final ACI aci;
  private final PNI pni;

  private ByteString aciByteString;
  private ByteString pniByteString;

  public ServiceIds(ACI aci, PNI pni) {
    this.aci = aci;
    this.pni = pni;
  }

  public ACI getAci() {
    return aci;
  }

  public PNI getPni() {
    return pni;
  }

  public PNI requirePni() {
    return Objects.requireNonNull(pni);
  }

  public boolean matches(UUID uuid) {
    return uuid.equals(aci.getRawUuid()) || (pni != null && uuid.equals(pni.getRawUuid()));
  }

  public boolean matches(ByteString uuid) {
    if (aciByteString == null) {
      aciByteString = aci.toByteString();
    }

    if (pniByteString == null && pni != null) {
      pniByteString = pni.toByteString();
    }

    return uuid.equals(aciByteString) || uuid.equals(pniByteString);
  }
}
