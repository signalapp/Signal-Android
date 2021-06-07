package org.thoughtcrime.securesms.conversation.v2.messages

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import kotlinx.android.synthetic.main.view_visible_message.view.*
import network.loki.messenger.R
import org.session.libsession.messaging.contacts.Contact.ContactContext
import org.session.libsession.utilities.ViewUtil
import org.session.libsignal.utilities.guava.Optional
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.util.DateUtils
import kotlin.math.roundToInt

class VisibleMessageView : LinearLayout {

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

    private fun setUpViewHierarchy() {
        LayoutInflater.from(context).inflate(R.layout.view_visible_message, this)
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
    // endregion

    // region Updating
    fun bind(message: MessageRecord, previous: MessageRecord?, next: MessageRecord?) {
        val sender = message.individualRecipient
        val senderSessionID = sender.address.serialize()
        val threadID = message.threadId
        val threadDB = DatabaseFactory.getThreadDatabase(context)
        val thread = threadDB.getRecipientForThreadId(threadID)
        val contactDB = DatabaseFactory.getSessionContactDatabase(context)
        val isGroupThread = (thread?.isGroupRecipient == true)
        // Show profile picture and sender name if this is a group thread AND
        // the message is incoming
        if (isGroupThread && !message.isOutgoing) {
            profilePictureContainer.visibility = View.VISIBLE
            profilePictureView.publicKey = senderSessionID
            // TODO: Set glide on the profile picture view and update it
            // TODO: Show crown if this is an open group and the user is a moderator; otherwise hide it
            senderNameTextView.visibility = View.VISIBLE
            val context = if (thread?.isOpenGroupRecipient == true) ContactContext.OPEN_GROUP else ContactContext.REGULAR
            senderNameTextView.text = contactDB.getContactWithSessionID(senderSessionID)?.displayName(context) ?: senderSessionID
        } else {
            profilePictureContainer.visibility = View.GONE
            senderNameTextView.visibility = View.GONE
        }
        // Date break
        dateBreakTextView.text = "The Ancient Past"
        dateBreakTextView.visibility = View.GONE // TODO: Set this correctly
        // Margins
        val messageContentViewLayoutParams = messageContentView.layoutParams as LinearLayout.LayoutParams
        if (isGroupThread) {
            messageContentViewLayoutParams.leftMargin = if (message.isOutgoing) resources.getDimension(R.dimen.very_large_spacing).toInt() else 0
        } else {
            messageContentViewLayoutParams.leftMargin = if (message.isOutgoing) resources.getDimension(R.dimen.very_large_spacing).toInt()
                else resources.getDimension(R.dimen.medium_spacing).toInt()
        }
        messageContentViewLayoutParams.rightMargin = if (message.isOutgoing) resources.getDimension(R.dimen.medium_spacing).toInt()
            else resources.getDimension(R.dimen.very_large_spacing).toInt()
        messageContentView.layoutParams = messageContentViewLayoutParams
        // Set inter-message spacing
        setMessageSpacing(message, previous, next, isGroupThread)
        // Gravity
        val gravity = if (message.isOutgoing) Gravity.RIGHT else Gravity.LEFT
        mainContainer.gravity = gravity or Gravity.BOTTOM
        // Populate content view
        messageContentView.bind(message)
    }

    private fun setMessageSpacing(current: MessageRecord, previous: MessageRecord?, next: MessageRecord?, isGroupThread: Boolean) {
        val isStartOfMessageCluster = (isStartOfMessageCluster(current, previous, isGroupThread))
        val topPadding = if (isStartOfMessageCluster) R.dimen.conversation_vertical_message_spacing_default
            else R.dimen.conversation_vertical_message_spacing_collapse
        ViewUtil.setPaddingTop(this, resources.getDimension(topPadding).roundToInt())
        val isEndOfMessageCluster = (isEndOfMessageCluster(current, previous, isGroupThread))
        val bottomPadding = if (isEndOfMessageCluster) R.dimen.conversation_vertical_message_spacing_default
            else R.dimen.conversation_vertical_message_spacing_collapse
        ViewUtil.setPaddingBottom(this, resources.getDimension(bottomPadding).roundToInt())
    }

    private fun isStartOfMessageCluster(current: MessageRecord, previous: MessageRecord?, isGroupThread: Boolean): Boolean {
        return if (isGroupThread) {
            previous == null || previous.isUpdate || !DateUtils.isSameDay(current.timestamp, previous.timestamp)
                || current.recipient.address != previous.recipient.address
        } else {
            previous == null || previous.isUpdate || !DateUtils.isSameDay(current.timestamp, previous.timestamp)
                || current.isOutgoing != previous.isOutgoing
        }
    }

    private fun isEndOfMessageCluster(current: MessageRecord, next: MessageRecord?, isGroupThread: Boolean): Boolean {
        return if (isGroupThread) {
            next == null || next.isUpdate || !DateUtils.isSameDay(current.timestamp, next.timestamp)
                || current.recipient.address != next.recipient.address
        } else {
            next == null || next.isUpdate || !DateUtils.isSameDay(current.timestamp, next.timestamp)
                || current.isOutgoing != next.isOutgoing
        }
    }

    fun recycle() {
        profilePictureView.recycle()
        messageContentView.recycle()
    }
    // endregion
}