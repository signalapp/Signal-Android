package org.whispersystems.signalservice.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class SendGroupMessageResponse {

  @JsonProperty
  private String[] uuids404;

  public SendGroupMessageResponse() {}

  public Set<ACI> getUnsentTargets() {
    Set<ACI> acis = new HashSet<>(uuids404.length);

    for (String raw : uuids404) {
      Optional<ACI> parsed = ACI.parse(raw);
      if (parsed.isPresent()) {
        acis.add(parsed.get());
      }
    }

    return acis;
  }
}
