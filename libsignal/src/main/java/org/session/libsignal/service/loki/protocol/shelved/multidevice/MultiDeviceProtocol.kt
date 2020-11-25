package org.session.libsignal.service.loki.protocol.shelved.multidevice

import org.session.libsignal.service.loki.database.LokiAPIDatabaseProtocol

public class MultiDeviceProtocol(private val apiDatabase: LokiAPIDatabaseProtocol) {

    // region Initialization
    companion object {

        public lateinit var shared: MultiDeviceProtocol

        public fun configureIfNeeded(apiDatabase: LokiAPIDatabaseProtocol) {
            if (Companion::shared.isInitialized) { return; }
            shared = MultiDeviceProtocol(apiDatabase)
        }
    }
    // endregion

    // region Utilities
    public fun getMasterDevice(publicKey: String): String? {
        return null
        /*
        val deviceLinks = apiDatabase.getDeviceLinks(publicKey)
        return deviceLinks.firstOrNull { it.slavePublicKey == publicKey }?.masterPublicKey
         */
    }

    public fun getSlaveDevices(publicKey: String): Set<String> {
        return setOf()
        /*
        val deviceLinks = apiDatabase.getDeviceLinks(publicKey)
        if (deviceLinks.isEmpty()) { return setOf() }
        return deviceLinks.map { it.slavePublicKey }.toSet()
         */
    }

    public fun getAllLinkedDevices(publicKey: String): Set<String> {
        return setOf( publicKey )
        /*
        val deviceLinks = apiDatabase.getDeviceLinks(publicKey)
        if (deviceLinks.isEmpty()) { return setOf( publicKey ) }
        return deviceLinks.flatMap { listOf( it.masterPublicKey, it.slavePublicKey ) }.toSet()
         */
    }
    // endregion
}
