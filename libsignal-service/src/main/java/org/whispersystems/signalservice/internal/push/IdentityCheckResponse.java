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
  private List<ServiceIdentityPair> serviceIdKeyPairs;

  public IdentityCheckResponse() {}

  // Visible for testing
  public IdentityCheckResponse(List<ServiceIdentityPair> serviceIdKeyPairs) {
    this.serviceIdKeyPairs = serviceIdKeyPairs;
  }

  public @Nullable List<ServiceIdentityPair> getServiceIdKeyPairs() {
    return serviceIdKeyPairs;
  }

  public static final class ServiceIdentityPair {

    @JsonProperty("uuid")
    @JsonDeserialize(using = JsonUtil.ServiceIdDeserializer.class)
    private ServiceId serviceId;

    @JsonProperty
    @JsonDeserialize(using = JsonUtil.IdentityKeyDeserializer.class)
    private IdentityKey identityKey;

    public ServiceIdentityPair() {}

    // Visible for testing
    public ServiceIdentityPair(ServiceId serviceId, IdentityKey identityKey) {
      this.serviceId   = serviceId;
      this.identityKey = identityKey;
    }

    public @Nullable ServiceId getServiceId() {
      return serviceId;
    }

    public @Nullable IdentityKey getIdentityKey() {
      return identityKey;
    }
  }
}
