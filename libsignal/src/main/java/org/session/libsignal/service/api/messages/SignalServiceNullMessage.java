package org.session.libsignal.service.api.messages;

import org.session.libsignal.service.loki.utilities.TTLUtilities;

public class SignalServiceNullMessage {

    public int getTTL() { return TTLUtilities.getTTL(TTLUtilities.MessageType.Ephemeral); }
}
