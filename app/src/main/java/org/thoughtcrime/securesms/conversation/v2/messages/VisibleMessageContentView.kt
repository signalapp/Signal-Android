package org.thoughtcrime.securesms.conversation.v2.messages

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.BlendModeColorFilterCompat
import androidx.core.graphics.BlendModeCompat
import kotlinx.android.synthetic.main.view_visible_message_content.view.*
import network.loki.messenger.R
import org.session.libsession.utilities.ThemeUtil
import org.thoughtcrime.securesms.database.model.MessageRecord

class VisibleMessageContentView : LinearLayout {

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
        LayoutInflater.from(context).inflate(R.layout.view_visible_message_content, this)
    }
    // endregion

    // region Updating
    fun bind(message: MessageRecord) {
        // Background
        // TODO: Set background to one of sent/received + top/middle/bottom + color
        val background = ResourcesCompat.getDrawable(resources, R.drawable.message_bubble_background, context.theme)!!
        val colorID = if (message.isOutgoing) R.attr.message_sent_background_color else R.attr.message_received_background_color
        val color = ThemeUtil.getThemedColor(context, colorID)
        val filter = BlendModeColorFilterCompat.createBlendModeColorFilterCompat(color, BlendModeCompat.SRC_IN)
        background.colorFilter = filter
        setBackground(background)
        // Body
        bodyTextView.text = message.body
        // TODO: All the other things that can show up in a visible message, such as link previews,
        //       attachments (incl. multiple at once), open group invitations, voice messages, etc.
    }
    // endregion
}