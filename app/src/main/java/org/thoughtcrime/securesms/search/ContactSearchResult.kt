package org.thoughtcrime.securesms.search

import org.thoughtcrime.securesms.recipients.Recipient

data class ContactSearchResult(val results: List<Recipient>, val query: String)
