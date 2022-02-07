package org.thoughtcrime.securesms.home.search

import org.session.libsession.messaging.contacts.Contact
import org.session.libsession.utilities.GroupRecord
import org.thoughtcrime.securesms.database.model.ThreadRecord
import org.thoughtcrime.securesms.search.model.MessageResult
import org.thoughtcrime.securesms.search.model.SearchResult

data class GlobalSearchResult(
        val query: String,
        val contacts: List<Contact>,
        val threads: List<GroupRecord>,
        val messages: List<MessageResult>
) {

    val isEmpty: Boolean
        get() = contacts.isEmpty() && threads.isEmpty() && messages.isEmpty()

    companion object {

        val EMPTY = GlobalSearchResult("", emptyList(), emptyList(), emptyList())
        const val SEARCH_LIMIT = 5

        fun from(searchResult: SearchResult): GlobalSearchResult {
            val query = searchResult.query
            val contactList = searchResult.contacts.toList()
            val threads = searchResult.conversations.toList()
            val messages = searchResult.messages.toList()
            searchResult.close()
            return GlobalSearchResult(query, contactList, threads, messages)
        }

    }
}
