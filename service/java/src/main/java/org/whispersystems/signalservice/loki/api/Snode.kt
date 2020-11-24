package org.whispersystems.signalservice.loki.api

public class Snode(val address: String, val port: Int, val publicKeySet: KeySet?) {

    val ip: String get() = address.removePrefix("https://")

    internal enum class Method(val rawValue: String) {
        /**
         * Only supported by snode targets.
         */
        GetSwarm("get_snodes_for_pubkey"),
        /**
         * Only supported by snode targets.
         */
        GetMessages("retrieve"),
        SendMessage("store")
    }

    data class KeySet(val ed25519Key: String, val x25519Key: String)

    override fun equals(other: Any?): Boolean {
        return if (other is Snode) {
            address == other.address && port == other.port
        } else {
            false
        }
    }

    override fun hashCode(): Int {
        return address.hashCode() xor port.hashCode()
    }

    override fun toString(): String { return "$address:$port" }
}
