package org.whispersystems.signalservice.loki.protocol.closedgroups

public interface SharedSenderKeysImplementationDelegate {

    public fun requestSenderKey(groupPublicKey: String, senderPublicKey: String)
}
