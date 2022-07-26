package org.whispersystems.signalservice.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import org.signal.libsignal.protocol.IdentityKey;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.internal.util.JsonUtil;

import java.util.List;

import javax.annotation.Nullable;

public class IdentityCheckResponse {

  @JsonProperty("elements")
  private List<AciIdentityPair> aciKeyPairs;

  public IdentityCheckResponse() {}

  // Visible for testing
  public IdentityCheckResponse(List<AciIdentityPair> aciKeyPairs) {
    this.aciKeyPairs = aciKeyPairs;
  }

  public @Nullable List<AciIdentityPair> getAciKeyPairs() {
    return aciKeyPairs;
  }

  public static final class AciIdentityPair {

    @JsonProperty
    @JsonDeserialize(using = JsonUtil.AciDeserializer.class)
    private ACI aci;

    @JsonProperty
    @JsonDeserialize(using = JsonUtil.IdentityKeyDeserializer.class)
    private IdentityKey identityKey;

    public AciIdentityPair() {}

    // Visible for testing
    public AciIdentityPair(ACI aci, IdentityKey identityKey) {
      this.aci         = aci;
      this.identityKey = identityKey;
    }

    public @Nullable ACI getAci() {
      return aci;
    }

    public @Nullable IdentityKey getIdentityKey() {
      return identityKey;
    }
  }
}
