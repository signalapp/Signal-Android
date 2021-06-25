package org.thoughtcrime.securesms.conversation.v2.messages

import android.content.Context
import android.graphics.drawable.Drawable
import android.text.util.Linkify
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.BlendModeColorFilterCompat
import androidx.core.graphics.BlendModeCompat
import androidx.core.text.toSpannable
import kotlinx.android.synthetic.main.view_visible_message_content.view.*
import network.loki.messenger.R
import org.session.libsession.utilities.ThemeUtil
import org.session.libsession.utilities.ViewUtil
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.conversation.v2.AlbumThumbnailView
import org.thoughtcrime.securesms.components.emoji.EmojiTextView
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.loki.utilities.*
import org.thoughtcrime.securesms.loki.utilities.MentionUtilities.highlightMentions
import org.thoughtcrime.securesms.mms.GlideRequests
import kotlin.math.roundToInt

class VisibleMessageContentView : LinearLayout {
    var onContentClick: (() -> Unit)? = null

    // region Lifecycle
    constructor(context: Context) : super(context) { initialize() }
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) { initialize() }
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { initialize() }

    private fun initialize() {
        LayoutInflater.from(context).inflate(R.layout.view_visible_message_content, this)
    }
    // endregion

    // region Updating
    fun bind(message: MessageRecord, isStartOfMessageCluster: Boolean, isEndOfMessageCluster: Boolean,
        glide: GlideRequests, maxWidth: Int, thread: Recipient) {
        // Background
        val background = getBackground(message.isOutgoing, isStartOfMessageCluster, isEndOfMessageCluster)
        val colorID = if (message.isOutgoing) R.attr.message_sent_background_color else R.attr.message_received_background_color
        val color = ThemeUtil.getThemedColor(context, colorID)
        val filter = BlendModeColorFilterCompat.createBlendModeColorFilterCompat(color, BlendModeCompat.SRC_IN)
        background.colorFilter = filter
        setBackground(background)
        // Body
        mainContainer.removeAllViews()
        onContentClick = null
        if (message is MmsMessageRecord && message.linkPreviews.isNotEmpty()) {
            val linkPreviewView = LinkPreviewView(context)
            linkPreviewView.bind(message, glide, background)
            mainContainer.addView(linkPreviewView)
            // Body text view is inside the link preview for layout convenience
        } else if (message is MmsMessageRecord && message.quote != null) {
            val quote = message.quote!!
            val quoteView = QuoteView(context, QuoteView.Mode.Regular)
            // The max content width is the max message bubble size - 2 times the horizontal padding - the
            // quote view content area's start margin. This unfortunately has to be calculated manually
            // here to get the layout right.
            val maxContentWidth = (maxWidth - 2 * resources.getDimension(R.dimen.medium_spacing) - toPx(16, resources)).roundToInt()
            quoteView.bind(quote.author.toString(), quote.text, quote.attachment, thread,
                message.isOutgoing, maxContentWidth, message.isOpenGroupInvitation)
            mainContainer.addView(quoteView)
            val bodyTextView = VisibleMessageContentView.getBodyTextView(context, message)
            ViewUtil.setPaddingTop(bodyTextView, 0)
            mainContainer.addView(bodyTextView)
        } else if (message is MmsMessageRecord && message.slideDeck.audioSlide != null) {
            val voiceMessageView = VoiceMessageView(context)
            voiceMessageView.bind(message, background)
            mainContainer.addView(voiceMessageView)
            // We have to use onContentClick (rather than a click listener directly on the voice
            // message view) so as to not interfere with all the other gestures.
            onContentClick = { voiceMessageView.togglePlayback() }
        } else if (message is MmsMessageRecord && message.slideDeck.documentSlide != null) {
            val documentView = DocumentView(context)
            documentView.bind(message, VisibleMessageContentView.getTextColor(context, message))
            mainContainer.addView(documentView)
        } else if (message is MmsMessageRecord && message.slideDeck.asAttachments().isNotEmpty()) {
            val albumThumbnailView = AlbumThumbnailView(context)
            mainContainer.addView(albumThumbnailView)
            // isStart and isEnd of cluster needed for calculating the mask for full bubble image groups
            // bind after add view because views are inflated and calculated during bind
            albumThumbnailView.bind(
                    glideRequests = glide,
                    message = message,
                    isStart = isStartOfMessageCluster,
                    isEnd = isEndOfMessageCluster,
                    clickListener = { slide ->
                        Log.d("Loki-UI","clicked to display the slide $slide")
                    },
                    downloadClickListener = { slide ->
                        // trigger download of content?
                        Log.d("Loki-UI","clicked to download the slide $slide")
                    },
                    readMoreListener = {
                        Log.d("Loki-UI", "clicked to read more the message $message")
                    }
            )
        } else if (message.isOpenGroupInvitation) {
            val openGroupInvitationView = OpenGroupInvitationView(context)
            openGroupInvitationView.bind(message, VisibleMessageContentView.getTextColor(context, message))
            mainContainer.addView(openGroupInvitationView)
        } else {
            val bodyTextView = VisibleMessageContentView.getBodyTextView(context, message)
            mainContainer.addView(bodyTextView)
        }
    }

    private fun getBackground(isOutgoing: Boolean, isStartOfMessageCluster: Boolean, isEndOfMessageCluster: Boolean): Drawable {
        val isSingleMessage = (isStartOfMessageCluster && isEndOfMessageCluster)
        @DrawableRes val backgroundID: Int
        if (isSingleMessage) {
            backgroundID = if (isOutgoing) R.drawable.message_bubble_background_sent_alone else R.drawable.message_bubble_background_received_alone
        } else if (isStartOfMessageCluster) {
            backgroundID = if (isOutgoing) R.drawable.message_bubble_background_sent_start else R.drawable.message_bubble_background_received_start
        } else if (isEndOfMessageCluster) {
            backgroundID = if (isOutgoing) R.drawable.message_bubble_background_sent_end else R.drawable.message_bubble_background_received_end
        } else {
            backgroundID = if (isOutgoing) R.drawable.message_bubble_background_sent_middle else R.drawable.message_bubble_background_received_middle
        }
        return ResourcesCompat.getDrawable(resources, backgroundID, context.theme)!!
    }

    fun recycle() {
        mainContainer.removeAllViews()
    }
    // endregion

    // region Convenience
    companion object {

        fun getBodyTextView(context: Context, message: MessageRecord): TextView {
            val result = EmojiTextView(context)
            val vPadding = context.resources.getDimension(R.dimen.small_spacing).toInt()
            val hPadding = toPx(12, context.resources)
            result.setPadding(hPadding, vPadding, hPadding, vPadding)
            result.setTextSize(TypedValue.COMPLEX_UNIT_PX, context.resources.getDimension(R.dimen.small_font_size))
            val color = getTextColor(context, message)
            result.setTextColor(color)
            result.setLinkTextColor(color)
            var body = message.body.toSpannable()
            Linkify.addLinks(body, Linkify.WEB_URLS)
            body = MentionUtilities.highlightMentions(body, message.isOutgoing, message.threadId, context);
            result.text = body
            return result
        }

        @ColorInt
        fun getTextColor(context: Context, message: MessageRecord): Int {
            val uiMode = UiModeUtilities.getUserSelectedUiMode(context)
            val colorID = if (message.isOutgoing) {
                if (uiMode == UiMode.NIGHT) R.color.black else R.color.white
            } else {
                if (uiMode == UiMode.NIGHT) R.color.white else R.color.black
            }
            return context.resources.getColorWithID(colorID, context.theme)
        }
    }
    // endregion
}