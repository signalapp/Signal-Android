package org.thoughtcrime.securesms.messagerequests

import android.content.Context
import android.database.Cursor
import org.thoughtcrime.securesms.dependencies.DatabaseComponent
import org.thoughtcrime.securesms.util.AbstractCursorLoader

class MessageRequestsLoader(context: Context) : AbstractCursorLoader(context) {

    override fun getCursor(): Cursor {
        return DatabaseComponent.get(context).threadDatabase().unapprovedConversationList
    }
}