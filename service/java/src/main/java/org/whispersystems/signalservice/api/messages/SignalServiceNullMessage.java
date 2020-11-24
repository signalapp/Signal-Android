package org.whispersystems.signalservice.api.messages;

import org.whispersystems.signalservice.loki.protocol.meta.TTLUtilities;

public class SignalServiceNullMessage {

    public int getTTL() { return TTLUtilities.getTTL(TTLUtilities.MessageType.Ephemeral); }
}
