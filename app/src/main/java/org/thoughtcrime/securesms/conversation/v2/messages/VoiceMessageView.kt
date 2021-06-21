package org.thoughtcrime.securesms.conversation.v2.messages

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import kotlinx.android.synthetic.main.view_voice_message.view.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.database.model.MessageRecord

class VoiceMessageView : LinearLayout {

    // region Lifecycle
    constructor(context: Context) : super(context) { initialize() }
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) { initialize() }
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { initialize() }

    private fun initialize() {
        LayoutInflater.from(context).inflate(R.layout.view_voice_message, this)
    }
    // endregion

    // region Updating
    fun bind(message: MessageRecord) {

    }

    fun recycle() {
        // TODO: Implement
    }
    // endregion
}