package org.thoughtcrime.securesms.search

import org.thoughtcrime.securesms.database.model.ThreadWithRecipient

data class ThreadSearchResult(val results: List<ThreadWithRecipient>, val query: String)
