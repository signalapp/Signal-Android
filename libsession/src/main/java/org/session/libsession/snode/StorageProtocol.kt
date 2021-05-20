package org.session.libsession.snode

import org.session.libsignal.utilities.Snode

interface SnodeStorageProtocol {

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
}