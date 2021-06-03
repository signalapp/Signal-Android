package org.thoughtcrime.securesms.conversation.v2.messages

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.BlendModeColorFilterCompat
import androidx.core.graphics.BlendModeCompat
import androidx.core.view.setMargins
import androidx.core.view.setPadding
import kotlinx.android.synthetic.main.view_visible_message_content.view.*
import network.loki.messenger.R
import org.session.libsession.utilities.ThemeUtil
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.loki.utilities.UiMode
import org.thoughtcrime.securesms.loki.utilities.UiModeUtilities
import org.thoughtcrime.securesms.loki.utilities.getColorWithID
import java.lang.IllegalStateException

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
        mainContainer.removeAllViews()
        if (message is MmsMessageRecord && message.linkPreviews.isNotEmpty()) {
            val linkPreviewView = LinkPreviewView(context)
            linkPreviewView.bind(message)
            mainContainer.addView(linkPreviewView)
        } else if (message is MmsMessageRecord && message.quote != null) {
            val quoteView = QuoteView(context)
            quoteView.bind(message)
            mainContainer.addView(quoteView)
        } else if (message is MmsMessageRecord && message.slideDeck.audioSlide != null) {
            val voiceMessageView = VoiceMessageView(context)
            voiceMessageView.bind(message)
            mainContainer.addView(voiceMessageView)
        } else if (message is MmsMessageRecord && message.slideDeck.documentSlide != null) {
            val documentView = DocumentView(context)
            documentView.bind(message)
            mainContainer.addView(documentView)
        } else if (message is MmsMessageRecord && message.slideDeck.asAttachments().isNotEmpty()) {
            throw IllegalStateException("Not yet implemented; we may want to use Signal's album view here.")
        } else {
            val bodyTextView = getBodyTextView(message)
            mainContainer.addView(bodyTextView)
        }
    }

    fun recycle() {
        mainContainer.removeAllViews()
    }
    // endregion

    // region Convenience
    private fun getBodyTextView(message: MessageRecord): TextView {
        val result = TextView(context)
        result.setPadding(resources.getDimension(R.dimen.small_spacing).toInt())
        result.text = message.body
        result.setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.medium_font_size))
        val uiMode = UiModeUtilities.getUserSelectedUiMode(context)
        val colorID = if (message.isOutgoing) {
            if (uiMode == UiMode.NIGHT) R.color.black else R.color.white
        } else {
            if (uiMode == UiMode.NIGHT) R.color.white else R.color.black
        }
        result.setTextColor(resources.getColorWithID(colorID, context.theme))
        return result
    }
    // endregion
}