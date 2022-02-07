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
import network.loki.messenger.R
import network.loki.messenger.databinding.ViewConversationBinding
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.conversation.v2.utilities.MentionUtilities.highlightMentions
import org.thoughtcrime.securesms.database.RecipientDatabase
import org.thoughtcrime.securesms.database.model.ThreadRecord
import org.thoughtcrime.securesms.mms.GlideRequests
import org.thoughtcrime.securesms.util.DateUtils
import java.util.Locale

class ConversationView : LinearLayout {
    private lateinit var binding: ViewConversationBinding
    private val screenWidth = Resources.getSystem().displayMetrics.widthPixels
    var thread: ThreadRecord? = null

    // region Lifecycle
    constructor(context: Context) : super(context) { initialize() }
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) { initialize() }
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { initialize() }

    private fun initialize() {
        binding = ViewConversationBinding.inflate(LayoutInflater.from(context), this, true)
        layoutParams = RecyclerView.LayoutParams(screenWidth, RecyclerView.LayoutParams.WRAP_CONTENT)
    }
    // endregion

    // region Updating
    fun bind(thread: ThreadRecord, isTyping: Boolean, glide: GlideRequests) {
        this.thread = thread
        background = if (thread.isPinned) {
            binding.conversationViewDisplayNameTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_pin, 0)
            ContextCompat.getDrawable(context, R.drawable.conversation_pinned_background)
        } else {
            binding.conversationViewDisplayNameTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0)
            ContextCompat.getDrawable(context, R.drawable.conversation_view_background)
        }
        binding.profilePictureView.glide = glide
        val unreadCount = thread.unreadCount
        if (thread.recipient.isBlocked) {
            binding.accentView.setBackgroundResource(R.color.destructive)
            binding.accentView.visibility = View.VISIBLE
        } else {
            binding.accentView.setBackgroundResource(R.color.accent)
            // Using thread.isRead we can determine if the last message was our own, and display it as 'read' even though previous messages may not be
            // This would also not trigger the disappearing message timer which may or may not be desirable
            binding.accentView.visibility = if (unreadCount > 0 && !thread.isRead) View.VISIBLE else View.INVISIBLE
        }
        val formattedUnreadCount = if (thread.isRead) {
            null
        } else {
            if (unreadCount < 10000) unreadCount.toString() else "9999+"
        }
        binding.unreadCountTextView.text = formattedUnreadCount
        val textSize = if (unreadCount < 10000) 12.0f else 9.0f
        binding.unreadCountTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, textSize)
        binding.unreadCountTextView.setTypeface(Typeface.DEFAULT, if (unreadCount < 100) Typeface.BOLD else Typeface.NORMAL)
        binding.unreadCountIndicator.isVisible = (unreadCount != 0 && !thread.isRead)
        val senderDisplayName = getUserDisplayName(thread.recipient)
                ?: thread.recipient.address.toString()
        binding.conversationViewDisplayNameTextView.text = senderDisplayName
        binding.timestampTextView.text = DateUtils.getDisplayFormattedTimeSpanString(context, Locale.getDefault(), thread.date)
        val recipient = thread.recipient
        binding.muteIndicatorImageView.isVisible = recipient.isMuted || recipient.notifyType != RecipientDatabase.NOTIFY_TYPE_ALL
        val drawableRes = if (recipient.isMuted || recipient.notifyType == RecipientDatabase.NOTIFY_TYPE_NONE) {
            R.drawable.ic_outline_notifications_off_24
        } else {
            R.drawable.ic_notifications_mentions
        }
        binding.muteIndicatorImageView.setImageResource(drawableRes)
        val rawSnippet = thread.getDisplayBody(context)
        val snippet = highlightMentions(rawSnippet, thread.threadId, context)
        binding.snippetTextView.text = snippet
        binding.snippetTextView.typeface = if (unreadCount > 0 && !thread.isRead) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        binding.snippetTextView.visibility = if (isTyping) View.GONE else View.VISIBLE
        if (isTyping) {
            binding.typingIndicatorView.startAnimation()
        } else {
            binding.typingIndicatorView.stopAnimation()
        }
        binding.typingIndicatorView.visibility = if (isTyping) View.VISIBLE else View.GONE
        binding.statusIndicatorImageView.visibility = View.VISIBLE
        when {
            !thread.isOutgoing -> binding.statusIndicatorImageView.visibility = View.GONE
            thread.isFailed -> {
                val drawable = ContextCompat.getDrawable(context, R.drawable.ic_error)?.mutate()
                drawable?.setTint(ContextCompat.getColor(context, R.color.destructive))
                binding.statusIndicatorImageView.setImageDrawable(drawable)
            }
            thread.isPending -> binding.statusIndicatorImageView.setImageResource(R.drawable.ic_circle_dot_dot_dot)
            thread.isRead -> binding.statusIndicatorImageView.setImageResource(R.drawable.ic_filled_circle_check)
            else -> binding.statusIndicatorImageView.setImageResource(R.drawable.ic_circle_check)
        }
        post {
            binding.profilePictureView.update(thread.recipient)
        }
    }

    fun recycle() {
        binding.profilePictureView.recycle()
    }

    private fun getUserDisplayName(recipient: Recipient): String? {
        return if (recipient.isLocalNumber) {
            context.getString(R.string.note_to_self)
        } else {
            recipient.name // Internally uses the Contact API
        }
    }
    // endregion
}
