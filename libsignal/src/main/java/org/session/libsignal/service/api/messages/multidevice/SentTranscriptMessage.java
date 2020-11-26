/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.session.libsignal.service.api.messages.multidevice;

import org.session.libsignal.libsignal.util.guava.Optional;
import org.session.libsignal.service.api.messages.SignalServiceDataMessage;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class SentTranscriptMessage {

  private final Optional<String>         destination;
  private final long                     timestamp;
  private final long                     expirationStartTimestamp;
  private final SignalServiceDataMessage message;
  private final Map<String, Boolean>     unidentifiedStatus;

  // Loki - Open groups
  public long messageServerID = -1;

  public SentTranscriptMessage(String destination, long timestamp, SignalServiceDataMessage message,
                               long expirationStartTimestamp, Map<String, Boolean> unidentifiedStatus)
  {
    this.destination              = Optional.of(destination);
    this.timestamp                = timestamp;
    this.message                  = message;
    this.expirationStartTimestamp = expirationStartTimestamp;
    this.unidentifiedStatus       = new HashMap<String, Boolean>(unidentifiedStatus);
  }

  public SentTranscriptMessage(long timestamp, SignalServiceDataMessage message) {
    this.destination              = Optional.absent();
    this.timestamp                = timestamp;
    this.message                  = message;
    this.expirationStartTimestamp = 0;
    this.unidentifiedStatus       = Collections.emptyMap();
  }

  public Optional<String> getDestination() {
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

  public boolean isUnidentified(String destination) {
    if (unidentifiedStatus.containsKey(destination)) {
      return unidentifiedStatus.get(destination);
    }
    return false;
  }
}
