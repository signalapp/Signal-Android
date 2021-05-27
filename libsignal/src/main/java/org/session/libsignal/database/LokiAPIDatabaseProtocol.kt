package org.session.libsignal.database

import org.session.libsignal.crypto.ecc.ECKeyPair
import org.session.libsignal.utilities.Snode
import java.util.*

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
    fun setUserCount(group: Long, server: String, newValue: Int)
    fun setUserCount(room: String, server: String, newValue: Int)
    fun getLastMessageServerID(room: String, server: String): Long?
    fun setLastMessageServerID(room: String, server: String, newValue: Long)
    fun getLastDeletionServerID(room: String, server: String): Long?
    fun setLastDeletionServerID(room: String, server: String, newValue: Long)
    fun getOpenGroupPublicKey(server: String): String?
    fun setOpenGroupPublicKey(server: String, newValue: String)
    fun getLastSnodePoolRefreshDate(): Date?
    fun setLastSnodePoolRefreshDate(newValue: Date)
    fun getUserX25519KeyPair(): ECKeyPair
    fun getClosedGroupEncryptionKeyPairs(groupPublicKey: String): List<ECKeyPair>
    fun getLatestClosedGroupEncryptionKeyPair(groupPublicKey: String): ECKeyPair?
    fun isClosedGroup(groupPublicKey: String): Boolean
}
