package org.thoughtcrime.securesms.search

import org.thoughtcrime.securesms.database.model.ThreadRecord

data class ThreadSearchResult(val results: List<ThreadRecord>, val query: String)
