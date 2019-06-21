package org.thoughtcrime.securesms.loki

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.sms.IncomingTextMessage

class FriendRequestView(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : LinearLayout(context, attrs, defStyleAttr) {
    var message: Any? = null
        set(newValue) {
            field = newValue
            kind = if (message is IncomingTextMessage) Kind.Incoming else Kind.Outgoing
        }
    var kind: Kind? = null
    var delegate: FriendRequestViewDelegate? = null

    // region Types
    enum class Kind { Incoming, Outgoing }
    // endregion

    // region Components
    private val topSpacer by lazy {
        val result = View(context)
        result.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 12)
        result
    }

    private val label by lazy {
        val result = TextView(context)
        result.setTextColor(resources.getColorWithID(R.color.core_grey_90, context.theme))
        // TODO: Typeface
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

    init {
        orientation = VERTICAL
        addView(topSpacer)
        addView(label)
//        if (kind == Kind.Incoming) {
            // Accept button
            val acceptButton = Button(context)
            acceptButton.text = "Accept"
            acceptButton.setTextColor(resources.getColorWithID(R.color.signal_primary, context.theme))
            acceptButton.setBackgroundColor(resources.getColorWithID(R.color.transparent, context.theme))
            // TODO: Typeface
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                acceptButton.elevation = 0f
                acceptButton.stateListAnimator = null
            }
            acceptButton.setOnClickListener { accept() }
            val acceptButtonLayoutParams = LayoutParams(0, convertToPixels(50, resources))
            acceptButtonLayoutParams.weight = 1f
            acceptButton.layoutParams = acceptButtonLayoutParams
            buttonLinearLayout.addView(acceptButton)
            // Reject button
            val rejectButton = Button(context)
            rejectButton.text = "Reject"
            rejectButton.setTextColor(resources.getColorWithID(R.color.red, context.theme))
            rejectButton.setBackgroundColor(resources.getColorWithID(R.color.transparent, context.theme))
            // TODO: Typeface
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                rejectButton.elevation = 0f
                rejectButton.stateListAnimator = null
            }
            rejectButton.setOnClickListener { reject() }
            val rejectButtonLayoutParams = LayoutParams(0, convertToPixels(50, resources))
            rejectButtonLayoutParams.weight = 1f
            rejectButton.layoutParams = rejectButtonLayoutParams
            buttonLinearLayout.addView(rejectButton)
            //
            buttonLinearLayout.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, convertToPixels(50, resources))
            addView(buttonLinearLayout)
//        }
        kind = Kind.Incoming // TODO: For debugging purposes
        updateUI()
        // TODO: Observe friend request status changes
    }
    // endregion

    // region Updating
    private fun updateUI() {
        when (kind) {
            Kind.Incoming -> {
//                val message = this.message as IncomingTextMessage
//                buttonLinearLayout.visibility = View.GONE // TODO: Base on friend request status
                val text = { // TODO: Base on friend request status
                    "You've received a friend request"
                }()
                label.text = text
            }
            Kind.Outgoing -> {
//                val message = this.message as OutgoingTextMessage
//                buttonLinearLayout.visibility = View.GONE
                val text = {
                    "You've sent a friend request"
                }()
                label.text = text
            }
        }
    }
    // endregion

    // region Interaction
    private fun accept() {
//        val message = this.message as IncomingTextMessage
        // TODO: Update message friend request status
//        delegate?.acceptFriendRequest(message)
    }

    private fun reject() {
//        val message = this.message as IncomingTextMessage
        // TODO: Update message friend request status
//        delegate?.rejectFriendRequest(message)
    }
    // endregion
}