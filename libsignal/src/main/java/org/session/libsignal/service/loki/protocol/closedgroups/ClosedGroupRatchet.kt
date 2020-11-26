package org.session.libsignal.service.loki.protocol.closedgroups

import org.session.libsignal.service.loki.utilities.prettifiedDescription

public class ClosedGroupRatchet(public val chainKey: String, public val keyIndex: Int, public val messageKeys: List<String>) {

    override fun equals(other: Any?): Boolean {
        return if (other is ClosedGroupRatchet) {
            chainKey == other.chainKey && keyIndex == other.keyIndex && messageKeys == other.messageKeys
        } else {
            false
        }
    }

    override fun hashCode(): Int {
        return chainKey.hashCode() xor keyIndex.hashCode() xor messageKeys.hashCode()
    }

    override fun toString(): String {
        return "[ chainKey : $chainKey, keyIndex : $keyIndex, messageKeys : ${messageKeys.prettifiedDescription()} ]"
    }
}
