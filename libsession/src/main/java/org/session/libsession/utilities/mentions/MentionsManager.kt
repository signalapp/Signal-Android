package org.session.libsession.utilities.mentions

import org.session.libsession.messaging.MessagingConfiguration
import org.session.libsignal.service.loki.database.LokiThreadDatabaseProtocol
import org.session.libsignal.service.loki.database.LokiUserDatabaseProtocol

class MentionsManager(private val userPublicKey: String, private val threadDatabase: LokiThreadDatabaseProtocol,
    private val userDatabase: LokiUserDatabaseProtocol) {
    var userPublicKeyCache = mutableMapOf<Long, Set<String>>() // Thread ID to set of user hex encoded public keys

    companion object {

        lateinit var shared: MentionsManager

        fun configureIfNeeded(userPublicKey: String, threadDatabase: LokiThreadDatabaseProtocol, userDatabase: LokiUserDatabaseProtocol) {
            if (::shared.isInitialized) { return; }
            shared = MentionsManager(userPublicKey, threadDatabase, userDatabase)
        }
    }

    fun cache(publicKey: String, threadID: Long) {
        val cache = userPublicKeyCache[threadID]
        if (cache != null) {
            userPublicKeyCache[threadID] = cache.plus(publicKey)
        } else {
            userPublicKeyCache[threadID] = setOf( publicKey )
        }
    }

    fun getMentionCandidates(query: String, threadID: Long): List<Mention> {
        // Prepare
        val cache = userPublicKeyCache[threadID] ?: return listOf()
        // Gather candidates
        val storage = MessagingConfiguration.shared.storage
        val publicChat = threadDatabase.getPublicChat(threadID)
        val openGroupV2 = storage.getV2OpenGroup(threadID.toString())
        var candidates: List<Mention> = cache.mapNotNull { publicKey ->
            val displayName: String?
            if (publicChat != null) {
                displayName = userDatabase.getServerDisplayName(publicChat.id, publicKey)
            } else if (openGroupV2 != null) {
                displayName = userDatabase.getServerDisplayName(openGroupV2.id, publicKey)
            } else {
                displayName = userDatabase.getDisplayName(publicKey)
            }
            if (displayName == null) { return@mapNotNull null }
            if (displayName.startsWith("Anonymous")) { return@mapNotNull null }
            Mention(publicKey, displayName)
        }
        candidates = candidates.filter { it.publicKey != userPublicKey }
        // Sort alphabetically first
        candidates.sortedBy { it.displayName }
        if (query.length >= 2) {
            // Filter out any non-matching candidates
            candidates = candidates.filter { it.displayName.toLowerCase().contains(query.toLowerCase()) }
            // Sort based on where in the candidate the query occurs
            candidates.sortedBy { it.displayName.toLowerCase().indexOf(query.toLowerCase()) }
        }
        // Return
        return candidates
    }
}
