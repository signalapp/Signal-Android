package org.thoughtcrime.securesms.conversation.v2.messages

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.text.Spannable
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.URLSpan
import android.text.util.Linkify
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MotionEvent
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.BlendModeColorFilterCompat
import androidx.core.graphics.BlendModeCompat
import androidx.core.text.getSpans
import androidx.core.text.toSpannable
import androidx.core.view.children
import network.loki.messenger.R
import network.loki.messenger.databinding.ViewVisibleMessageContentBinding
import okhttp3.HttpUrl
import org.session.libsession.utilities.ThemeUtil
import org.session.libsession.utilities.ViewUtil
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.components.emoji.EmojiTextView
import org.thoughtcrime.securesms.conversation.v2.ConversationActivityV2
import org.thoughtcrime.securesms.conversation.v2.ModalUrlBottomSheet
import org.thoughtcrime.securesms.conversation.v2.components.AlbumThumbnailView
import org.thoughtcrime.securesms.conversation.v2.utilities.MentionUtilities
import org.thoughtcrime.securesms.conversation.v2.utilities.ModalURLSpan
import org.thoughtcrime.securesms.conversation.v2.utilities.TextUtilities.getIntersectedModalSpans
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.mms.GlideRequests
import org.thoughtcrime.securesms.util.SearchUtil
import org.thoughtcrime.securesms.util.UiModeUtilities
import org.thoughtcrime.securesms.util.getColorWithID
import org.thoughtcrime.securesms.util.toPx
import java.util.Locale
import kotlin.math.roundToInt

class VisibleMessageContentView : LinearLayout {
    private lateinit var binding:  ViewVisibleMessageContentBinding
    var onContentClick: ((event: MotionEvent) -> Unit)? = null
    var onContentDoubleTap: (() -> Unit)? = null
    var delegate: VisibleMessageContentViewDelegate? = null
    var indexInAdapter: Int = -1

    // region Lifecycle
    constructor(context: Context) : super(context) { initialize() }
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) { initialize() }
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { initialize() }

    private fun initialize() {
        binding = ViewVisibleMessageContentBinding.inflate(LayoutInflater.from(context), this, true)
    }
    // endregion

    // region Updating
    fun bind(message: MessageRecord, isStartOfMessageCluster: Boolean, isEndOfMessageCluster: Boolean,
        glide: GlideRequests, maxWidth: Int, thread: Recipient, searchQuery: String?, contactIsTrusted: Boolean) {
        // Background
        val background = getBackground(message.isOutgoing, isStartOfMessageCluster, isEndOfMessageCluster)
        val colorID = if (message.isOutgoing) R.attr.message_sent_background_color else R.attr.message_received_background_color
        val color = ThemeUtil.getThemedColor(context, colorID)
        val filter = BlendModeColorFilterCompat.createBlendModeColorFilterCompat(color, BlendModeCompat.SRC_IN)
        background.colorFilter = filter
        setBackground(background)
        // Body
        binding.mainContainer.removeAllViews()
        onContentClick = null
        onContentDoubleTap = null
        if (message.isDeleted) {
            val deletedMessageView = DeletedMessageView(context)
            deletedMessageView.bind(message, getTextColor(context,message))
            binding.mainContainer.addView(deletedMessageView)
        } else if (message is MmsMessageRecord && message.linkPreviews.isNotEmpty()) {
            val linkPreviewView = LinkPreviewView(context)
            linkPreviewView.bind(message, glide, isStartOfMessageCluster, isEndOfMessageCluster, searchQuery)
            binding.mainContainer.addView(linkPreviewView)
            onContentClick = { event -> linkPreviewView.calculateHit(event) }
            // Body text view is inside the link preview for layout convenience
        } else if (message is MmsMessageRecord && message.quote != null) {
            val quote = message.quote!!
            val quoteView = QuoteView(context, QuoteView.Mode.Regular)
            // The max content width is the max message bubble size - 2 times the horizontal padding - 2
            // times the horizontal margin. This unfortunately has to be calculated manually
            // here to get the layout right.
            val maxContentWidth = (maxWidth - 2 * resources.getDimension(R.dimen.medium_spacing) - 2 * toPx(16, resources)).roundToInt()
            val quoteText = if (quote.isOriginalMissing) {
                context.getString(R.string.QuoteView_original_missing)
            } else {
                quote.text
            }
            quoteView.bind(quote.author.toString(), quoteText, quote.attachment, thread,
                message.isOutgoing, maxContentWidth, message.isOpenGroupInvitation, message.threadId,
                quote.isOriginalMissing, glide)
            binding.mainContainer.addView(quoteView)
            val bodyTextView = getBodyTextView(context, message, searchQuery)
            ViewUtil.setPaddingTop(bodyTextView, 0)
            binding.mainContainer.addView(bodyTextView)
            onContentClick = { event ->
                val r = Rect()
                quoteView.getGlobalVisibleRect(r)
                if (r.contains(event.rawX.roundToInt(), event.rawY.roundToInt())) {
                    delegate?.scrollToMessageIfPossible(quote.id)
                } else {
                    bodyTextView.getIntersectedModalSpans(event).forEach { span ->
                        span.onClick(bodyTextView)
                    }
                }
            }
        } else if (message is MmsMessageRecord && message.slideDeck.audioSlide != null) {
            // Audio attachment
            if (contactIsTrusted || message.isOutgoing) {
                val voiceMessageView = VoiceMessageView(context)
                voiceMessageView.indexInAdapter = indexInAdapter
                voiceMessageView.delegate = context as? ConversationActivityV2
                voiceMessageView.bind(message, isStartOfMessageCluster, isEndOfMessageCluster)
                binding.mainContainer.addView(voiceMessageView)
                // We have to use onContentClick (rather than a click listener directly on the voice
                // message view) so as to not interfere with all the other gestures.
                onContentClick = { voiceMessageView.togglePlayback() }
                onContentDoubleTap = { voiceMessageView.handleDoubleTap() }
            } else {
                val untrustedView = UntrustedAttachmentView(context)
                untrustedView.bind(UntrustedAttachmentView.AttachmentType.AUDIO, getTextColor(context,message))
                binding.mainContainer.addView(untrustedView)
                onContentClick = { untrustedView.showTrustDialog(message.individualRecipient) }
            }
        } else if (message is MmsMessageRecord && message.slideDeck.documentSlide != null) {
            // Document attachment
            if (contactIsTrusted || message.isOutgoing) {
                val documentView = DocumentView(context)
                documentView.bind(message, getTextColor(context, message))
                binding.mainContainer.addView(documentView)
            } else {
                val untrustedView = UntrustedAttachmentView(context)
                untrustedView.bind(UntrustedAttachmentView.AttachmentType.DOCUMENT, getTextColor(context,message))
                binding.mainContainer.addView(untrustedView)
                onContentClick = { untrustedView.showTrustDialog(message.individualRecipient) }
            }
        } else if (message is MmsMessageRecord && message.slideDeck.asAttachments().isNotEmpty()) {
            // Images/Video attachment
            if (contactIsTrusted || message.isOutgoing) {
                val albumThumbnailView = AlbumThumbnailView(context)
                binding.mainContainer.addView(albumThumbnailView)
                // isStart and isEnd of cluster needed for calculating the mask for full bubble image groups
                // bind after add view because views are inflated and calculated during bind
                albumThumbnailView.bind(
                        glideRequests = glide,
                        message = message,
                        isStart = isStartOfMessageCluster,
                        isEnd = isEndOfMessageCluster
                )
                onContentClick = { event ->
                    albumThumbnailView.calculateHitObject(event, message, thread)
                }
            } else {
                val untrustedView = UntrustedAttachmentView(context)
                untrustedView.bind(UntrustedAttachmentView.AttachmentType.MEDIA, getTextColor(context,message))
                binding.mainContainer.addView(untrustedView)
                onContentClick = { untrustedView.showTrustDialog(message.individualRecipient) }
            }
        } else if (message.isOpenGroupInvitation) {
            val openGroupInvitationView = OpenGroupInvitationView(context)
            openGroupInvitationView.bind(message, getTextColor(context, message))
            binding.mainContainer.addView(openGroupInvitationView)
            onContentClick = { openGroupInvitationView.joinOpenGroup() }
        } else {
            val bodyTextView = getBodyTextView(context, message, searchQuery)
            binding.mainContainer.addView(bodyTextView)
            onContentClick = { event ->
                // intersectedModalSpans should only be a list of one item
                bodyTextView.getIntersectedModalSpans(event).forEach { span ->
                    span.onClick(bodyTextView)
                }
            }
        }
    }

    private fun getBackground(isOutgoing: Boolean, isStartOfMessageCluster: Boolean, isEndOfMessageCluster: Boolean): Drawable {
        val isSingleMessage = (isStartOfMessageCluster && isEndOfMessageCluster)
        @DrawableRes val backgroundID = when {
            isSingleMessage -> {
                if (isOutgoing) R.drawable.message_bubble_background_sent_alone else R.drawable.message_bubble_background_received_alone
            }
            isStartOfMessageCluster -> {
                if (isOutgoing) R.drawable.message_bubble_background_sent_start else R.drawable.message_bubble_background_received_start
            }
            isEndOfMessageCluster -> {
                if (isOutgoing) R.drawable.message_bubble_background_sent_end else R.drawable.message_bubble_background_received_end
            }
            else -> {
                if (isOutgoing) R.drawable.message_bubble_background_sent_middle else R.drawable.message_bubble_background_received_middle
            }
        }
        return ResourcesCompat.getDrawable(resources, backgroundID, context.theme)!!
    }

    fun recycle() {
        binding.mainContainer.removeAllViews()
    }

    fun playVoiceMessage() {
        binding.mainContainer.children.forEach { view ->
            if (view is VoiceMessageView) {
                return@forEach view.togglePlayback()
            }
        }
    }
    // endregion

    // region Convenience
    companion object {

        fun getBodyTextView(context: Context, message: MessageRecord, searchQuery: String?): TextView {
            val result = EmojiTextView(context)
            val vPadding = context.resources.getDimension(R.dimen.small_spacing).toInt()
            val hPadding = toPx(12, context.resources)
            result.setPadding(hPadding, vPadding, hPadding, vPadding)
            result.setTextSize(TypedValue.COMPLEX_UNIT_PX, context.resources.getDimension(R.dimen.small_font_size))
            val color = getTextColor(context, message)
            result.setTextColor(color)
            result.setLinkTextColor(color)
            val body = getBodySpans(context, message, searchQuery)
            result.text = body
            return result
        }

        fun getBodySpans(context: Context, message: MessageRecord, searchQuery: String?): Spannable {
            var body = message.body.toSpannable()

            body = MentionUtilities.highlightMentions(body, message.isOutgoing, message.threadId, context)
            body = SearchUtil.getHighlightedSpan(Locale.getDefault(),
                { BackgroundColorSpan(Color.WHITE) }, body, searchQuery)
            body = SearchUtil.getHighlightedSpan(Locale.getDefault(),
                { ForegroundColorSpan(Color.BLACK) }, body, searchQuery)

            Linkify.addLinks(body, Linkify.WEB_URLS)

            // replace URLSpans with ModalURLSpans
            body.getSpans<URLSpan>(0, body.length).toList().forEach { urlSpan ->
                val updatedUrl = urlSpan.url.let { HttpUrl.parse(it).toString() }
                val replacementSpan = ModalURLSpan(updatedUrl) { url ->
                    val activity = context as AppCompatActivity
                    ModalUrlBottomSheet(url).show(activity.supportFragmentManager, "Open URL Dialog")
                }
                val start = body.getSpanStart(urlSpan)
                val end = body.getSpanEnd(urlSpan)
                val flags = body.getSpanFlags(urlSpan)
                body.removeSpan(urlSpan)
                body.setSpan(replacementSpan, start, end, flags)
            }
            return body
        }

        @ColorInt
        fun getTextColor(context: Context, message: MessageRecord): Int {
            val isDayUiMode = UiModeUtilities.isDayUiMode(context)
            val colorID = if (message.isOutgoing) {
                if (isDayUiMode) R.color.white else R.color.black
            } else {
                if (isDayUiMode) R.color.black else R.color.white
            }
            return context.resources.getColorWithID(colorID, context.theme)
        }
    }
    // endregion
}

interface VisibleMessageContentViewDelegate {

    fun scrollToMessageIfPossible(timestamp: Long)
}