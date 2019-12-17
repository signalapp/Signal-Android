package org.thoughtcrime.securesms.loki.redesign.views

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import kotlinx.android.synthetic.main.conversation_view.view.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.database.model.ThreadRecord
import org.thoughtcrime.securesms.loki.LokiAPIUtilities.populateUserHexEncodedPublicKeyCacheIfNeeded
import org.thoughtcrime.securesms.loki.MentionUtilities.highlightMentions
import org.thoughtcrime.securesms.util.DateUtils
import java.util.*

class ConversationView : LinearLayout {
    var thread: ThreadRecord? = null

    // region Lifecycle
    companion object {

        fun get(context: Context, parent: ViewGroup?): ConversationView {
            return LayoutInflater.from(context).inflate(R.layout.conversation_view, parent, false) as ConversationView
        }
    }

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)
    // endregion

    // region Updating
    fun bind(thread: ThreadRecord) {
        this.thread = thread
        populateUserHexEncodedPublicKeyCacheIfNeeded(thread.threadId, context) // FIXME: This is a terrible place to do this
        unreadMessagesIndicatorView.visibility = if (thread.unreadCount > 0) View.VISIBLE else View.INVISIBLE
        val senderDisplayName = if (thread.recipient.isLocalNumber) context.getString(R.string.note_to_self) else thread.recipient.name
        displayNameTextView.text = senderDisplayName
        timestampTextView.text = DateUtils.getBriefRelativeTimeSpanString(context, Locale.getDefault(), thread.date)
        val rawSnippet = thread.getDisplayBody(context)
        val snippet = highlightMentions(rawSnippet, thread.threadId, context)
        snippetTextView.text = snippet
        snippetTextView.typeface = if (thread.unreadCount > 0) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }
    // endregion
}