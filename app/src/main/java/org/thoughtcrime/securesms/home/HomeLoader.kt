package org.thoughtcrime.securesms.home

import android.content.Context
import android.database.Cursor
import org.thoughtcrime.securesms.dependencies.DatabaseComponent
import org.thoughtcrime.securesms.util.AbstractCursorLoader

class HomeLoader(context: Context, val onNewCursor: (Cursor?) -> Unit) : AbstractCursorLoader(context) {

    override fun getCursor(): Cursor {
        return DatabaseComponent.get(context).threadDatabase().approvedConversationList
    }

    override fun deliverResult(newCursor: Cursor?) {
        super.deliverResult(newCursor)
        onNewCursor(newCursor)
    }
}