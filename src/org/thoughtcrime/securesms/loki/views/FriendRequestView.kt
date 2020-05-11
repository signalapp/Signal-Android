package org.thoughtcrime.securesms.loki.views

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.github.ybq.android.spinkit.style.DoubleBounce
import network.loki.messenger.R
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.database.model.MediaMmsMessageRecord
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.loki.getColorWithID
import org.thoughtcrime.securesms.loki.toPx
import org.whispersystems.signalservice.loki.protocol.todo.LokiMessageFriendRequestStatus

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
        result.setTextColor(resources.getColorWithID(R.color.text, context.theme))
        result.textAlignment = TextView.TEXT_ALIGNMENT_CENTER
        result.setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.small_font_size))
        result
    }

    private val buttonLinearLayout by lazy {
        val result = LinearLayout(context)
        result.orientation = HORIZONTAL
        result.setPadding(0, resources.getDimension(R.dimen.medium_spacing).toInt(), 0, 0)
        result
    }

    private val loaderContainer by lazy {
        val result = LinearLayout(context)
        val layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, toPx(50, resources))
        result.layoutParams = layoutParams
        result.gravity = Gravity.CENTER
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
        setPadding(toPx(48, resources), 0, toPx(48, resources), 0)
        addView(topSpacer)
        addView(label)
        if (!message!!.isOutgoing) {
            val loader = ProgressBar(context)
            loader.isIndeterminate = true
            loader.indeterminateDrawable = DoubleBounce()
            val loaderLayoutParams = LayoutParams(LayoutParams.MATCH_PARENT, toPx(24, resources))
            loader.layoutParams = loaderLayoutParams
            loaderContainer.addView(loader)
            addView(loaderContainer)
            fun button(): Button {
                val result = Button(context)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    result.elevation = 0f
                    result.stateListAnimator = null
                }
                result.setTextColor(resources.getColorWithID(R.color.text, context.theme))
                result.setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.small_font_size))
                result.isAllCaps = false
                result.setPadding(0, 0, 0, 0)
                val buttonLayoutParams = LayoutParams(0, resources.getDimension(R.dimen.small_button_height).toInt())
                buttonLayoutParams.weight = 1f
                result.layoutParams = buttonLayoutParams
                return result
            }
            val rejectButton = button()
            rejectButton.text = resources.getString(R.string.view_friend_request_reject_button_title)
            rejectButton.setBackgroundResource(R.drawable.unimportant_dialog_button_background)
            rejectButton.setOnClickListener { reject() }
            buttonLinearLayout.addView(rejectButton)
            val acceptButton = button()
            acceptButton.text = resources.getString(R.string.view_friend_request_accept_button_title)
            acceptButton.setBackgroundResource(R.drawable.prominent_dialog_button_background)
            val acceptButtonLayoutParams = acceptButton.layoutParams as LayoutParams
            acceptButtonLayoutParams.setMargins(resources.getDimension(R.dimen.medium_spacing).toInt(), 0, 0, 0)
            acceptButton.layoutParams = acceptButtonLayoutParams
            acceptButton.setOnClickListener { accept() }
            buttonLinearLayout.addView(acceptButton)
            buttonLinearLayout.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, toPx(50, resources))
            addView(buttonLinearLayout)
        }
    }

    private fun updateUI() {
        val message = message
        val lokiMessageDatabase = DatabaseFactory.getLokiMessageDatabase(context)
        val contactID = DatabaseFactory.getThreadDatabase(context).getRecipientForThreadId(message!!.threadId)!!.address.toString()
        val contactDisplayName = DatabaseFactory.getLokiUserDatabase(context).getDisplayName(contactID) ?: contactID
        val friendRequestStatus = lokiMessageDatabase.getFriendRequestStatus(message.id)
        if (message is MediaMmsMessageRecord) {
            visibility = View.GONE
            return
        }
        if (!message.isOutgoing) {
            visibility = if (friendRequestStatus == LokiMessageFriendRequestStatus.NONE) View.GONE else View.VISIBLE
            buttonLinearLayout.visibility = if (friendRequestStatus != LokiMessageFriendRequestStatus.REQUEST_PENDING) View.GONE else View.VISIBLE
            loaderContainer.visibility = if (friendRequestStatus == LokiMessageFriendRequestStatus.REQUEST_SENDING) View.VISIBLE else View.GONE
            val formatID = when (friendRequestStatus) {
                LokiMessageFriendRequestStatus.NONE, LokiMessageFriendRequestStatus.REQUEST_SENDING, LokiMessageFriendRequestStatus.REQUEST_FAILED -> return
                LokiMessageFriendRequestStatus.REQUEST_PENDING -> R.string.view_friend_request_incoming_pending_message
                LokiMessageFriendRequestStatus.REQUEST_ACCEPTED -> R.string.view_friend_request_incoming_accepted_message
                LokiMessageFriendRequestStatus.REQUEST_REJECTED -> R.string.view_friend_request_incoming_declined_message
                LokiMessageFriendRequestStatus.REQUEST_EXPIRED -> R.string.view_friend_request_incoming_expired_message
            }
            label.text = resources.getString(formatID, contactDisplayName)
        } else {
            visibility = if (friendRequestStatus == LokiMessageFriendRequestStatus.NONE) View.GONE else View.VISIBLE
            buttonLinearLayout.visibility = View.GONE
            loaderContainer.visibility = View.GONE
            val formatID = when (friendRequestStatus) {
                LokiMessageFriendRequestStatus.NONE -> return
                LokiMessageFriendRequestStatus.REQUEST_SENDING, LokiMessageFriendRequestStatus.REQUEST_FAILED -> null
                LokiMessageFriendRequestStatus.REQUEST_PENDING, LokiMessageFriendRequestStatus.REQUEST_REJECTED -> R.string.view_friend_request_outgoing_pending_message
                LokiMessageFriendRequestStatus.REQUEST_ACCEPTED -> R.string.view_friend_request_outgoing_accepted_message
                LokiMessageFriendRequestStatus.REQUEST_EXPIRED -> R.string.view_friend_request_outgoing_expired_message
            }
            if (formatID != null) {
                label.text = resources.getString(formatID, contactDisplayName)
            }
            label.visibility = if (formatID != null) View.VISIBLE else View.GONE
            topSpacer.visibility = label.visibility
        }
    }
    // endregion

    // region Interaction
    private fun accept() {
        val lokiMessageDatabase = DatabaseFactory.getLokiMessageDatabase(context)
        lokiMessageDatabase.setFriendRequestStatus(message!!.id, LokiMessageFriendRequestStatus.REQUEST_SENDING)
        updateUI()
        delegate?.acceptFriendRequest(message!!)
    }

    private fun reject() {
        val lokiMessageDatabase = DatabaseFactory.getLokiMessageDatabase(context)
        lokiMessageDatabase.setFriendRequestStatus(message!!.id, LokiMessageFriendRequestStatus.REQUEST_REJECTED)
        updateUI()
        delegate?.rejectFriendRequest(message!!)
    }
    // endregion
}

// region Delegate
interface FriendRequestViewDelegate {
    /**
     * Implementations of this method should update the thread's friend request status
     * and send a friend request accepted message.
     */
    fun acceptFriendRequest(friendRequest: MessageRecord)
    /**
     * Implementations of this method should update the thread's friend request status
     * and remove the pre keys associated with the contact.
     */
    fun rejectFriendRequest(friendRequest: MessageRecord)
}
// endregion