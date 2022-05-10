/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.api.messages.multidevice;


import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceStoryMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceStoryMessageRecipient;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class SentTranscriptMessage {

  private final Optional<SignalServiceAddress>          destination;
  private final long                                    timestamp;
  private final long                                    expirationStartTimestamp;
  private final Optional<SignalServiceDataMessage>      message;
  private final Map<String, Boolean>                    unidentifiedStatusBySid;
  private final Map<String, Boolean>                    unidentifiedStatusByE164;
  private final Set<SignalServiceAddress>               recipients;
  private final boolean                                 isRecipientUpdate;
  private final Optional<SignalServiceStoryMessage>     storyMessage;
  private final Set<SignalServiceStoryMessageRecipient> storyMessageRecipients;

  public SentTranscriptMessage(Optional<SignalServiceAddress> destination,
                               long timestamp,
                               Optional<SignalServiceDataMessage> message,
                               long expirationStartTimestamp,
                               Map<SignalServiceAddress, Boolean> unidentifiedStatus,
                               boolean isRecipientUpdate,
                               Optional<SignalServiceStoryMessage> storyMessage,
                               Set<SignalServiceStoryMessageRecipient> storyMessageRecipients)
  {
    this.destination              = destination;
    this.timestamp                = timestamp;
    this.message                  = message;
    this.expirationStartTimestamp = expirationStartTimestamp;
    this.unidentifiedStatusBySid  = new HashMap<>();
    this.unidentifiedStatusByE164 = new HashMap<>();
    this.recipients               = unidentifiedStatus.keySet();
    this.isRecipientUpdate        = isRecipientUpdate;
    this.storyMessage             = storyMessage;
    this.storyMessageRecipients   = storyMessageRecipients;

    for (Map.Entry<SignalServiceAddress, Boolean> entry : unidentifiedStatus.entrySet()) {
      unidentifiedStatusBySid.put(entry.getKey().getServiceId().toString(), entry.getValue());

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

  public Optional<SignalServiceDataMessage> getDataMessage() {
    return message;
  }

  public Optional<SignalServiceStoryMessage> getStoryMessage() {
    return storyMessage;
  }

  public Set<SignalServiceStoryMessageRecipient> getStoryMessageRecipients() {
    return storyMessageRecipients;
  }

  public boolean isUnidentified(ServiceId serviceId) {
    return isUnidentified(serviceId.toString());
  }

  public boolean isUnidentified(String destination) {
    if (unidentifiedStatusBySid.containsKey(destination)) {
      return unidentifiedStatusBySid.get(destination);
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
