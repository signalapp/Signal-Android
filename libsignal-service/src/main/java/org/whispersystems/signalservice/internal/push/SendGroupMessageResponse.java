package org.whispersystems.signalservice.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.signal.libsignal.protocol.logging.Log;
import org.whispersystems.signalservice.api.push.ServiceId;

import java.util.HashSet;
import java.util.Set;

public class SendGroupMessageResponse {

  private static final String TAG = SendGroupMessageResponse.class.getSimpleName();

  // Contains serialized ServiceIds
  @JsonProperty
  private String[] uuids404;

  public SendGroupMessageResponse() {}

  public Set<ServiceId> getUnsentTargets() {
    String[]       uuids      = uuids404 != null ? uuids404 : new String[0];
    Set<ServiceId> serviceIds = new HashSet<>(uuids.length);

    for (String raw : uuids) {
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
