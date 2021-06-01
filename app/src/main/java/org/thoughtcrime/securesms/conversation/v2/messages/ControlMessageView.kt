package org.thoughtcrime.securesms.conversation.v2.messages

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import kotlinx.android.synthetic.main.view_control_message.view.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.database.model.MessageRecord

class ControlMessageView : LinearLayout {

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
        LayoutInflater.from(context).inflate(R.layout.view_control_message, this)
    }
    // endregion

    // region Updating
    fun bind(message: MessageRecord) {
        // TODO: Localize strings, make the view look better, handle closed group control messages
        if (message.isExpirationTimerUpdate) { textView.text = "Expiration timer update" }
        else if (message.isScreenshotNotification) { textView.text = "Screenshot notification" }
        else if (message.isMediaSavedNotification) { textView.text = "Media saved notification" }
        else { textView.text = "Control message not yet handled" }
    }

    fun recycle() {
        // TODO: Implement
    }
    // endregion
}