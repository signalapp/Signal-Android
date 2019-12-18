package org.thoughtcrime.securesms.loki.redesign.views

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import kotlinx.android.synthetic.main.view_conversation.view.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.database.model.ThreadRecord
import org.thoughtcrime.securesms.loki.LokiAPIUtilities.populateUserHexEncodedPublicKeyCacheIfNeeded
import org.thoughtcrime.securesms.loki.MentionUtilities.highlightMentions
import org.thoughtcrime.securesms.util.DateUtils
import org.whispersystems.signalservice.loki.api.LokiAPI
import java.util.*

class ConversationView : LinearLayout {
    var thread: ThreadRecord? = null

    // region Lifecycle
    constructor(context: Context) : super(context) {
        setUpViewHierarchy()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        setUpViewHierarchy()
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        setUpViewHierarchy()
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        setUpViewHierarchy()
    }

    private fun setUpViewHierarchy() {
        val inflater = context.applicationContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val contentView = inflater.inflate(R.layout.view_conversation, null)
        addView(contentView)
    }
    // endregion

    // region Updating
    fun bind(thread: ThreadRecord) {
        this.thread = thread
        populateUserHexEncodedPublicKeyCacheIfNeeded(thread.threadId, context) // FIXME: This is a terrible place to do this
        unreadMessagesIndicatorView.visibility = if (thread.unreadCount > 0) View.VISIBLE else View.INVISIBLE
        if (thread.recipient.isGroupRecipient) {
            val users = LokiAPI.userHexEncodedPublicKeyCache[thread.threadId]?.toList() ?: listOf()
            val randomUsers = users.sorted() // Sort to provide a level of stability
            profilePictureView.hexEncodedPublicKey = randomUsers.getOrNull(0) ?: ""
            profilePictureView.additionalHexEncodedPublicKey = randomUsers.getOrNull(1) ?: ""
            profilePictureView.isRSSFeed = thread.recipient.name == "Loki News" || thread.recipient.name == "Loki Messenger Updates"
        } else {
            profilePictureView.hexEncodedPublicKey = thread.recipient.address.toString()
            profilePictureView.additionalHexEncodedPublicKey = null
            profilePictureView.isRSSFeed = false
        }
        profilePictureView.update()
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