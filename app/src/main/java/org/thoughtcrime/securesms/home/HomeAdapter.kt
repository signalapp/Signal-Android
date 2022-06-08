package org.thoughtcrime.securesms.home

import org.thoughtcrime.securesms.database.model.ThreadRecord

interface ConversationClickListener {
    fun onConversationClick(thread: ThreadRecord)
    fun onLongConversationClick(thread: ThreadRecord)
}