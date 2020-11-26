package org.session.libsignal.service.loki.protocol.closedgroups

public interface SharedSenderKeysImplementationDelegate {

    public fun requestSenderKey(groupPublicKey: String, senderPublicKey: String)
}
