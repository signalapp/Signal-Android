package org.thoughtcrime.securesms.conversation.v2.messages

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.core.content.res.ResourcesCompat
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
        iconImageView.visibility = View.GONE
        if (message.isExpirationTimerUpdate) {
            iconImageView.setImageDrawable(ResourcesCompat.getDrawable(resources, R.drawable.ic_timer, context.theme))
            iconImageView.visibility = View.VISIBLE
        }
        textView.text = message.getDisplayBody(context)
    }

    fun recycle() {
        // TODO: Implement
    }
    // endregion
}