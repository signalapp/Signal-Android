package org.thoughtcrime.securesms.conversation.v2.components

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import kotlinx.android.synthetic.main.view_conversation_typing_container.view.*
import network.loki.messenger.R
import org.session.libsession.utilities.recipients.Recipient

class TypingIndicatorViewContainer : LinearLayout {

    constructor(context: Context) : super(context) { initialize() }
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) { initialize() }
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { initialize() }

    private fun initialize() {
        LayoutInflater.from(context).inflate(R.layout.view_conversation_typing_container, this)
    }

    fun setTypists(typists: List<Recipient>) {
        if (typists.isEmpty()) { typingIndicator.stopAnimation(); return }
        typingIndicator.startAnimation()
    }
}