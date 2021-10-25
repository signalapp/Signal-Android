package org.whispersystems.signalservice.api.messages.calls;

import org.whispersystems.signalservice.internal.push.SignalServiceProtos;

public class OpaqueMessage {

  private final byte[]  opaque;
  private final Urgency urgency;

  public OpaqueMessage(byte[] opaque, Urgency urgency) {
    this.opaque  = opaque;
    this.urgency = urgency == null ? Urgency.DROPPABLE : urgency;
  }

  public byte[] getOpaque() {
    return opaque;
  }

  public Urgency getUrgency() {
    return urgency;
  }

  public enum Urgency {
    DROPPABLE,
    HANDLE_IMMEDIATELY;

    public SignalServiceProtos.CallMessage.Opaque.Urgency toProto() {
      if (this == HANDLE_IMMEDIATELY) {
        return SignalServiceProtos.CallMessage.Opaque.Urgency.HANDLE_IMMEDIATELY;
      }
      return SignalServiceProtos.CallMessage.Opaque.Urgency.DROPPABLE;
    }
  }
}
