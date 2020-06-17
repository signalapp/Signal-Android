package org.whispersystems.signalservice.internal.push;

import java.util.List;

public final class SignalServiceMessagesResult {
  private final List<SignalServiceEnvelopeEntity> envelopes;
  private final long serverDeliveredTimestamp;

  SignalServiceMessagesResult(List<SignalServiceEnvelopeEntity> envelopes, long serverDeliveredTimestamp) {
    this.envelopes                = envelopes;
    this.serverDeliveredTimestamp = serverDeliveredTimestamp;
  }

  public List<SignalServiceEnvelopeEntity> getEnvelopes() {
    return envelopes;
  }

  public long getServerDeliveredTimestamp() {
    return serverDeliveredTimestamp;
  }
}
