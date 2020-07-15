package org.thoughtcrime.securesms.loki.views

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import kotlinx.android.synthetic.main.view_conversation.view.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.database.model.ThreadRecord
import org.thoughtcrime.securesms.loki.utilities.MentionManagerUtilities.populateUserPublicKeyCacheIfNeeded
import org.thoughtcrime.securesms.loki.utilities.MentionUtilities.highlightMentions
import org.thoughtcrime.securesms.mms.GlideRequests
import org.thoughtcrime.securesms.util.DateUtils
import org.whispersystems.signalservice.loki.protocol.mentions.MentionsManager
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
    fun bind(thread: ThreadRecord, isTyping: Boolean, glide: GlideRequests) {
        this.thread = thread
        populateUserPublicKeyCacheIfNeeded(thread.threadId, context) // FIXME: This is a terrible place to do this
        unreadMessagesIndicatorView.visibility = if (thread.unreadCount > 0) View.VISIBLE else View.INVISIBLE
        if (thread.recipient.isGroupRecipient) {
            if ("Session Public Chat" == thread.recipient.name) {
                profilePictureView.publicKey = ""
                profilePictureView.isRSSFeed = true
            } else {
                val users = MentionsManager.shared.userPublicKeyCache[thread.threadId]?.toList() ?: listOf()
                val randomUsers = users.sorted() // Sort to provide a level of stability
                profilePictureView.publicKey = randomUsers.getOrNull(0) ?: ""
                profilePictureView.additionalPublicKey = randomUsers.getOrNull(1) ?: ""
                profilePictureView.isRSSFeed = thread.recipient.name == "Loki News" || thread.recipient.name == "Session Updates"
            }
        } else {
            profilePictureView.publicKey = thread.recipient.address.toString()
            profilePictureView.additionalPublicKey = null
            profilePictureView.isRSSFeed = false
        }
        profilePictureView.glide = glide
        profilePictureView.update()
        val senderDisplayName = if (thread.recipient.isLocalNumber) context.getString(R.string.note_to_self) else if (!thread.recipient.name.isNullOrEmpty()) thread.recipient.name else thread.recipient.address.toString()
        displayNameTextView.text = senderDisplayName
        timestampTextView.text = DateUtils.getBriefRelativeTimeSpanString(context, Locale.getDefault(), thread.date)
        muteIndicatorImageView.visibility = if (thread.recipient.isMuted) VISIBLE else GONE
        val rawSnippet = thread.getDisplayBody(context)
        val snippet = highlightMentions(rawSnippet, thread.threadId, context)
        snippetTextView.text = snippet
        snippetTextView.typeface = if (thread.unreadCount > 0) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        snippetTextView.visibility = if (isTyping) View.GONE else View.VISIBLE
        if (isTyping) {
            typingIndicatorView.startAnimation()
        } else {
            typingIndicatorView.stopAnimation()
        }
        typingIndicatorView.visibility = if (isTyping) View.VISIBLE else View.GONE
        statusIndicatorImageView.visibility = View.VISIBLE
        when {
            !thread.isOutgoing || thread.isVerificationStatusChange -> statusIndicatorImageView.visibility = View.GONE
            thread.isFailed -> statusIndicatorImageView.setImageResource(R.drawable.ic_error)
            thread.isPending -> statusIndicatorImageView.setImageResource(R.drawable.ic_circle_dot_dot_dot)
            thread.isRemoteRead -> statusIndicatorImageView.setImageResource(R.drawable.ic_filled_circle_check)
            else -> statusIndicatorImageView.setImageResource(R.drawable.ic_circle_check)
        }
    }
    // endregion
}