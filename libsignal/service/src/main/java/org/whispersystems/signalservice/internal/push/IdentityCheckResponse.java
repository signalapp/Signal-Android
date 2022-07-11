package org.whispersystems.signalservice.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import org.signal.libsignal.protocol.IdentityKey;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.internal.util.JsonUtil;

import java.util.List;

import javax.annotation.Nullable;

public class IdentityCheckResponse {

  @JsonProperty("elements")
  private List<AciIdentityPair> aciKeyPairs;

  public @Nullable List<AciIdentityPair> getAciKeyPairs() {
    return aciKeyPairs;
  }

  public static final class AciIdentityPair {

    @JsonProperty
    @JsonDeserialize(using = JsonUtil.ServiceIdDeserializer.class)
    private ServiceId aci;

    @JsonProperty
    @JsonDeserialize(using = JsonUtil.IdentityKeyDeserializer.class)
    private IdentityKey identityKey;

    public @Nullable ServiceId getAci() {
      return aci;
    }

    public @Nullable IdentityKey getIdentityKey() {
      return identityKey;
    }
  }
}
