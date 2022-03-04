package org.thoughtcrime.securesms.conversation.v2

import android.content.Context
import android.database.Cursor
import org.thoughtcrime.securesms.dependencies.DatabaseComponent
import org.thoughtcrime.securesms.util.AbstractCursorLoader

class ConversationLoader(
    private val threadID: Long,
    private val reverse: Boolean,
    context: Context
) : AbstractCursorLoader(context) {

    override fun getCursor(): Cursor {
        return DatabaseComponent.get(context).mmsSmsDatabase().getConversation(threadID, reverse)
    }
}