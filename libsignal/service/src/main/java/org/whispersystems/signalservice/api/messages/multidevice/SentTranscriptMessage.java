/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.api.messages.multidevice;

import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class SentTranscriptMessage {

  private final Optional<SignalServiceAddress> destination;
  private final long                           timestamp;
  private final long                           expirationStartTimestamp;
  private final SignalServiceDataMessage       message;
  private final Map<String, Boolean>           unidentifiedStatusByUuid;
  private final Map<String, Boolean>           unidentifiedStatusByE164;
  private final Set<SignalServiceAddress>      recipients;
  private final boolean                        isRecipientUpdate;

  public SentTranscriptMessage(Optional<SignalServiceAddress> destination, long timestamp, SignalServiceDataMessage message,
                               long expirationStartTimestamp, Map<SignalServiceAddress, Boolean> unidentifiedStatus,
                               boolean isRecipientUpdate)
  {
    this.destination              = destination;
    this.timestamp                = timestamp;
    this.message                  = message;
    this.expirationStartTimestamp = expirationStartTimestamp;
    this.unidentifiedStatusByUuid = new HashMap<>();
    this.unidentifiedStatusByE164 = new HashMap<>();
    this.recipients               = unidentifiedStatus.keySet();
    this.isRecipientUpdate        = isRecipientUpdate;

    for (Map.Entry<SignalServiceAddress, Boolean> entry : unidentifiedStatus.entrySet()) {
      if (entry.getKey().getUuid().isPresent()) {
        unidentifiedStatusByUuid.put(entry.getKey().getUuid().get().toString(), entry.getValue());
      }
      if (entry.getKey().getNumber().isPresent()) {
        unidentifiedStatusByE164.put(entry.getKey().getNumber().get(), entry.getValue());
      }
    }
  }

  public Optional<SignalServiceAddress> getDestination() {
    return destination;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public long getExpirationStartTimestamp() {
    return expirationStartTimestamp;
  }

  public SignalServiceDataMessage getMessage() {
    return message;
  }

  public boolean isUnidentified(UUID uuid) {
    return isUnidentified(uuid.toString());
  }

  public boolean isUnidentified(String destination) {
    if (unidentifiedStatusByUuid.containsKey(destination)) {
      return unidentifiedStatusByUuid.get(destination);
    } else if (unidentifiedStatusByE164.containsKey(destination)) {
      return unidentifiedStatusByE164.get(destination);
    } else {
      return false;
    }
  }

  public Set<SignalServiceAddress> getRecipients() {
    return recipients;
  }

  public boolean isRecipientUpdate() {
    return isRecipientUpdate;
  }
}
