package org.thoughtcrime.securesms.home

import android.content.Context
import android.content.res.Resources
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.view_conversation.view.*
import network.loki.messenger.R
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.conversation.v2.utilities.MentionUtilities.highlightMentions
import org.thoughtcrime.securesms.database.RecipientDatabase
import org.thoughtcrime.securesms.database.model.ThreadRecord
import org.thoughtcrime.securesms.mms.GlideRequests
import org.thoughtcrime.securesms.util.DateUtils
import java.util.*

class ConversationView : LinearLayout {
    private val screenWidth = Resources.getSystem().displayMetrics.widthPixels
    var thread: ThreadRecord? = null

    // region Lifecycle
    constructor(context: Context) : super(context) { initialize() }
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) { initialize() }
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { initialize() }

    private fun initialize() {
        LayoutInflater.from(context).inflate(R.layout.view_conversation, this)
        layoutParams = RecyclerView.LayoutParams(screenWidth, RecyclerView.LayoutParams.WRAP_CONTENT)
    }
    // endregion

    // region Updating
    fun bind(thread: ThreadRecord, isTyping: Boolean, glide: GlideRequests) {
        this.thread = thread
        profilePictureView.glide = glide
        val unreadCount = thread.unreadCount
        if (thread.recipient.isBlocked) {
            accentView.setBackgroundResource(R.color.destructive)
            accentView.visibility = View.VISIBLE
        } else {
            accentView.setBackgroundResource(R.color.accent)
            // Using thread.isRead we can determine if the last message was our own, and display it as 'read' even though previous messages may not be
            // This would also not trigger the disappearing message timer which may or may not be desirable
            accentView.visibility = if (unreadCount > 0 && !thread.isRead) View.VISIBLE else View.INVISIBLE
        }
        val formattedUnreadCount = if (thread.isRead) {
            null
        } else {
            if (unreadCount < 100) unreadCount.toString() else "99+"
        }
        unreadCountTextView.text = formattedUnreadCount
        val textSize = if (unreadCount < 100) 12.0f else 9.0f
        unreadCountTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, textSize)
        unreadCountTextView.setTypeface(Typeface.DEFAULT, if (unreadCount < 100) Typeface.BOLD else Typeface.NORMAL)
        unreadCountIndicator.isVisible = (unreadCount != 0 && !thread.isRead)
        val senderDisplayName = getUserDisplayName(thread.recipient)
                ?: thread.recipient.address.toString()
        conversationViewDisplayNameTextView.text = senderDisplayName
        timestampTextView.text = DateUtils.getDisplayFormattedTimeSpanString(context, Locale.getDefault(), thread.date)
        val recipient = thread.recipient
        muteIndicatorImageView.isVisible = recipient.isMuted || recipient.notifyType != RecipientDatabase.NOTIFY_TYPE_ALL
        val drawableRes = if (recipient.isMuted || recipient.notifyType == RecipientDatabase.NOTIFY_TYPE_NONE) {
            R.drawable.ic_outline_notifications_off_24
        } else {
            R.drawable.ic_notifications_mentions
        }
        muteIndicatorImageView.setImageResource(drawableRes)
        val rawSnippet = thread.getDisplayBody(context)
        val snippet = highlightMentions(rawSnippet, thread.threadId, context)
        snippetTextView.text = snippet
        snippetTextView.typeface = if (unreadCount > 0 && !thread.isRead) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        snippetTextView.visibility = if (isTyping) View.GONE else View.VISIBLE
        if (isTyping) {
            typingIndicatorView.startAnimation()
        } else {
            typingIndicatorView.stopAnimation()
        }
        typingIndicatorView.visibility = if (isTyping) View.VISIBLE else View.GONE
        statusIndicatorImageView.visibility = View.VISIBLE
        when {
            !thread.isOutgoing -> statusIndicatorImageView.visibility = View.GONE
            thread.isFailed -> {
                val drawable = ContextCompat.getDrawable(context, R.drawable.ic_error)?.mutate()
                drawable?.setTint(ContextCompat.getColor(context, R.color.destructive))
                statusIndicatorImageView.setImageDrawable(drawable)
            }
            thread.isPending -> statusIndicatorImageView.setImageResource(R.drawable.ic_circle_dot_dot_dot)
            thread.isRead -> statusIndicatorImageView.setImageResource(R.drawable.ic_filled_circle_check)
            else -> statusIndicatorImageView.setImageResource(R.drawable.ic_circle_check)
        }
        post {
            profilePictureView.update(thread.recipient, thread.threadId)
        }
    }

    fun recycle() {
        profilePictureView.recycle()
    }

    private fun getUserDisplayName(recipient: Recipient): String? {
        if (recipient.isLocalNumber) {
            return context.getString(R.string.note_to_self)
        } else {
            return recipient.name // Internally uses the Contact API
        }
    }
    // endregion
}