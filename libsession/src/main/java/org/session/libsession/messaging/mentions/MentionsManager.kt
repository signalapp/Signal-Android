package org.session.libsession.messaging.mentions

import org.session.libsession.messaging.MessagingModuleConfiguration

import org.session.libsignal.database.LokiUserDatabaseProtocol

class MentionsManager(private val userPublicKey: String, private val userDatabase: LokiUserDatabaseProtocol) {
    var userPublicKeyCache = mutableMapOf<Long, Set<String>>() // Thread ID to set of user hex encoded public keys

    companion object {

        public lateinit var shared: MentionsManager

        public fun configureIfNeeded(userPublicKey: String, userDatabase: LokiUserDatabaseProtocol) {
            if (::shared.isInitialized) { return; }
            shared = MentionsManager(userPublicKey, userDatabase)
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
        val publicChat = MessagingModuleConfiguration.shared.messageDataProvider.getOpenGroup(threadID)
        var candidates: List<Mention> = cache.mapNotNull { publicKey ->
            val displayName: String?
            if (publicChat != null) {
                displayName = userDatabase.getServerDisplayName(publicChat.id, publicKey)
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
