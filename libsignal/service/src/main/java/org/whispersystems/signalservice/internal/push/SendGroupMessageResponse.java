package org.whispersystems.signalservice.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.whispersystems.libsignal.logging.Log;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class SendGroupMessageResponse {

  private static final String TAG = SendGroupMessageResponse.class.getSimpleName();

  @JsonProperty
  private String[] uuids404;

  public SendGroupMessageResponse() {}

  public Set<ServiceId> getUnsentTargets() {
    Set<ServiceId> serviceIds = new HashSet<>(uuids404.length);

    for (String raw : uuids404) {
      ServiceId parsed = ServiceId.parseOrNull(raw);
      if (parsed != null) {
        serviceIds.add(parsed);
      } else {
        Log.w(TAG, "Failed to parse ServiceId!");
      }
    }

    return serviceIds;
  }
}
