package org.thoughtcrime.securesms.loki

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.whispersystems.signalservice.loki.messaging.LokiMessageFriendRequestStatus

class FriendRequestView(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : LinearLayout(context, attrs, defStyleAttr) {
    private var isUISetUp = false
    private var message: MessageRecord? = null
    var delegate: FriendRequestViewDelegate? = null

    // region Components
    private val topSpacer by lazy {
        val result = View(context)
        result.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, toPx(12, resources))
        result
    }

    private val label by lazy {
        val result = TextView(context)
        result.setTextColor(resources.getColorWithID(R.color.core_grey_90, context.theme))
        result.textAlignment = TextView.TEXT_ALIGNMENT_CENTER
        result
    }

    private val buttonLinearLayout by lazy {
        val result = LinearLayout(context)
        result.orientation = HORIZONTAL
        result
    }
    // endregion

    // region Initialization
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context) : this(context, null)
    // endregion

    // region Updating
    fun update(message: MessageRecord) {
        this.message = message
        setUpUIIfNeeded()
        updateUI()
    }

    private fun setUpUIIfNeeded() {
        if (isUISetUp) { return }
        isUISetUp = true
        orientation = VERTICAL
        addView(topSpacer)
        addView(label)
        if (!message!!.isOutgoing) {
            fun button(): Button {
                val result = Button(context)
                result.setBackgroundColor(resources.getColorWithID(R.color.transparent, context.theme))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    result.elevation = 0f
                    result.stateListAnimator = null
                }
                val layoutParams = LayoutParams(0, toPx(50, resources))
                layoutParams.weight = 1f
                result.layoutParams = layoutParams
                return result
            }
            val acceptButton = button()
            acceptButton.text = resources.getString(R.string.view_friend_request_accept_button_title)
            acceptButton.setTextColor(resources.getColorWithID(R.color.signal_primary, context.theme))
            acceptButton.setOnClickListener { accept() }
            buttonLinearLayout.addView(acceptButton)
            val rejectButton = button()
            rejectButton.text = resources.getString(R.string.view_friend_request_reject_button_title)
            rejectButton.setTextColor(resources.getColorWithID(R.color.red, context.theme))
            rejectButton.setOnClickListener { reject() }
            buttonLinearLayout.addView(rejectButton)
            buttonLinearLayout.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, toPx(50, resources))
            addView(buttonLinearLayout)
        }
        // TODO: Observe friend request status changes
    }

    private fun updateUI() {
        val database = DatabaseFactory.getLokiMessageFriendRequestDatabase(context)
        val contactID = DatabaseFactory.getThreadDatabase(context).getRecipientForThreadId(message!!.threadId)!!.address.toString()
        if (!message!!.isOutgoing) {
            val friendRequestStatus = database.getFriendRequestStatus(message!!.id)
            visibility = if (friendRequestStatus == LokiMessageFriendRequestStatus.NONE) View.GONE else View.VISIBLE
            buttonLinearLayout.visibility = if (friendRequestStatus != LokiMessageFriendRequestStatus.REQUEST_PENDING) View.GONE else View.VISIBLE
            val formatID = when (friendRequestStatus) {
                LokiMessageFriendRequestStatus.NONE, LokiMessageFriendRequestStatus.REQUEST_SENDING_OR_FAILED -> return
                LokiMessageFriendRequestStatus.REQUEST_PENDING -> R.string.view_friend_request_incoming_pending_message
                LokiMessageFriendRequestStatus.REQUEST_ACCEPTED -> R.string.view_friend_request_incoming_accepted_message
                LokiMessageFriendRequestStatus.REQUEST_REJECTED -> R.string.view_friend_request_incoming_declined_message
                LokiMessageFriendRequestStatus.REQUEST_EXPIRED -> R.string.view_friend_request_incoming_expired_message
            }
            label.text = resources.getString(formatID, contactID)
        } else {
            val friendRequestStatus = database.getFriendRequestStatus(message!!.id)
            visibility = if (friendRequestStatus == LokiMessageFriendRequestStatus.NONE) View.GONE else View.VISIBLE
            buttonLinearLayout.visibility = View.GONE
            val formatID = when (friendRequestStatus) {
                LokiMessageFriendRequestStatus.NONE -> return
                LokiMessageFriendRequestStatus.REQUEST_SENDING_OR_FAILED -> null
                LokiMessageFriendRequestStatus.REQUEST_PENDING, LokiMessageFriendRequestStatus.REQUEST_REJECTED -> R.string.view_friend_request_outgoing_pending_message
                LokiMessageFriendRequestStatus.REQUEST_ACCEPTED -> R.string.view_friend_request_outgoing_accepted_message
                LokiMessageFriendRequestStatus.REQUEST_EXPIRED -> R.string.view_friend_request_outgoing_expired_message
            }
            if (formatID != null) {
                label.text = resources.getString(formatID, contactID)
            }
            label.visibility = if (formatID != null) View.VISIBLE else View.GONE
            topSpacer.visibility = label.visibility
        }
    }
    // endregion

    // region Interaction
    private fun accept() {
        val database = DatabaseFactory.getLokiMessageFriendRequestDatabase(context)
        database.setFriendRequestStatus(message!!.id, LokiMessageFriendRequestStatus.REQUEST_ACCEPTED)
        delegate?.acceptFriendRequest(message!!)
    }

    private fun reject() {
        val database = DatabaseFactory.getLokiMessageFriendRequestDatabase(context)
        database.setFriendRequestStatus(message!!.id, LokiMessageFriendRequestStatus.REQUEST_REJECTED)
        delegate?.rejectFriendRequest(message!!)
    }
    // endregion
}