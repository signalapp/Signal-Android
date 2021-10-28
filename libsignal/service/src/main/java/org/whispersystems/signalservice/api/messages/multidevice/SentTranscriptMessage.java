/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.api.messages.multidevice;

import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class SentTranscriptMessage {

  private final Optional<SignalServiceAddress> destination;
  private final long                           timestamp;
  private final long                           expirationStartTimestamp;
  private final SignalServiceDataMessage       message;
  private final Map<String, Boolean>           unidentifiedStatusByAci;
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
    this.unidentifiedStatusByAci  = new HashMap<>();
    this.unidentifiedStatusByE164 = new HashMap<>();
    this.recipients               = unidentifiedStatus.keySet();
    this.isRecipientUpdate        = isRecipientUpdate;

    for (Map.Entry<SignalServiceAddress, Boolean> entry : unidentifiedStatus.entrySet()) {
      unidentifiedStatusByAci.put(entry.getKey().getAci().toString(), entry.getValue());

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

  public boolean isUnidentified(ACI aci) {
    return isUnidentified(aci.toString());
  }

  public boolean isUnidentified(String destination) {
    if (unidentifiedStatusByAci.containsKey(destination)) {
      return unidentifiedStatusByAci.get(destination);
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
