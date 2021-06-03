package org.thoughtcrime.securesms.conversation.v2

import android.content.Context
import android.database.Cursor
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.util.AbstractCursorLoader

class ConversationLoader(private val threadID: Long, context: Context) : AbstractCursorLoader(context) {

    override fun getCursor(): Cursor {
        return DatabaseFactory.getMmsSmsDatabase(context).getConversation(threadID)
    }
}