package org.whispersystems.signalservice.loki.database

import org.whispersystems.signalservice.loki.api.Snode
import org.whispersystems.signalservice.loki.protocol.shelved.multidevice.DeviceLink

interface LokiAPIDatabaseProtocol {

    fun getSnodePool(): Set<Snode>
    fun setSnodePool(newValue: Set<Snode>)
    fun getOnionRequestPaths(): List<List<Snode>>
    fun clearOnionRequestPaths()
    fun setOnionRequestPaths(newValue: List<List<Snode>>)
    fun getSwarm(publicKey: String): Set<Snode>?
    fun setSwarm(publicKey: String, newValue: Set<Snode>)
    fun getLastMessageHashValue(snode: Snode, publicKey: String): String?
    fun setLastMessageHashValue(snode: Snode, publicKey: String, newValue: String)
    fun getReceivedMessageHashValues(publicKey: String): Set<String>?
    fun setReceivedMessageHashValues(publicKey: String, newValue: Set<String>)
    fun getAuthToken(server: String): String?
    fun setAuthToken(server: String, newValue: String?)
    fun getLastMessageServerID(group: Long, server: String): Long?
    fun setLastMessageServerID(group: Long, server: String, newValue: Long)
    fun getLastDeletionServerID(group: Long, server: String): Long?
    fun setLastDeletionServerID(group: Long, server: String, newValue: Long)
    fun setUserCount(group: Long, server: String, newValue: Int)
    fun getSessionRequestSentTimestamp(publicKey: String): Long?
    fun setSessionRequestSentTimestamp(publicKey: String, newValue: Long)
    fun getSessionRequestProcessedTimestamp(publicKey: String): Long?
    fun setSessionRequestProcessedTimestamp(publicKey: String, newValue: Long)
    fun getOpenGroupPublicKey(server: String): String?
    fun setOpenGroupPublicKey(server: String, newValue: String)
    fun setOpenGroupProfilePictureURL(group: Long, server: String, newValue: String)
    fun getOpenGroupProfilePictureURL(group: Long, server: String): String?

    // region Deprecated
    fun getDeviceLinks(publicKey: String): Set<DeviceLink>
    fun clearDeviceLinks(publicKey: String)
    fun addDeviceLink(deviceLink: DeviceLink)
    fun removeDeviceLink(deviceLink: DeviceLink)
    // endregion
}
