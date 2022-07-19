package org.thoughtcrime.securesms.database

import android.annotation.SuppressLint
import android.content.Context
import org.session.libsession.utilities.Debouncer
import org.thoughtcrime.securesms.ApplicationContext

class ConversationNotificationDebouncer(private val context: Context) {
    private val threadIDs = mutableSetOf<Long>()
    private val handler = (context.applicationContext as ApplicationContext).conversationListNotificationHandler
    private val debouncer = Debouncer(handler, 100)

    companion object {
        @SuppressLint("StaticFieldLeak")
        lateinit var shared: ConversationNotificationDebouncer

        @Synchronized
        fun get(context: Context): ConversationNotificationDebouncer {
            if (::shared.isInitialized) { return shared }
            shared = ConversationNotificationDebouncer(context)
            return shared
        }
    }

    fun notify(threadID: Long) {
        threadIDs.add(threadID)
        debouncer.publish { publish() }
    }

    private fun publish() {
        for (threadID in threadIDs.toList()) {
            context.contentResolver.notifyChange(DatabaseContentProviders.Conversation.getUriForThread(threadID), null)
        }
        threadIDs.clear()
    }
}