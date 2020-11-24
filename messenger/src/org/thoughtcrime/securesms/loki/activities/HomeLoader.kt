package org.thoughtcrime.securesms.loki.activities

import android.content.Context
import android.database.Cursor
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.util.AbstractCursorLoader

class HomeLoader(context: Context) : AbstractCursorLoader(context) {

    override fun getCursor(): Cursor {
        return DatabaseFactory.getThreadDatabase(context).conversationList
    }
}