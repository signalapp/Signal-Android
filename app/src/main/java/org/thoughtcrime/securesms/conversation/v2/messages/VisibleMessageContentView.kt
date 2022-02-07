package org.thoughtcrime.securesms.conversation.v2.messages

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.text.Spannable
import android.text.StaticLayout
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.URLSpan
import android.text.util.Linkify
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.BlendModeColorFilterCompat
import androidx.core.graphics.BlendModeCompat
import androidx.core.text.getSpans
import androidx.core.text.toSpannable
import androidx.core.view.isVisible
import network.loki.messenger.R
import network.loki.messenger.databinding.ViewVisibleMessageContentBinding
import okhttp3.HttpUrl
import org.session.libsession.utilities.ThemeUtil
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.conversation.v2.ConversationActivityV2
import org.thoughtcrime.securesms.conversation.v2.ModalUrlBottomSheet
import org.thoughtcrime.securesms.conversation.v2.utilities.MentionUtilities
import org.thoughtcrime.securesms.conversation.v2.utilities.ModalURLSpan
import org.thoughtcrime.securesms.conversation.v2.utilities.TextUtilities.getIntersectedModalSpans
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.database.model.SmsMessageRecord
import org.thoughtcrime.securesms.mms.GlideRequests
import org.thoughtcrime.securesms.util.SearchUtil
import org.thoughtcrime.securesms.util.UiModeUtilities
import org.thoughtcrime.securesms.util.getColorWithID
import org.thoughtcrime.securesms.util.toPx
import java.util.*
import kotlin.math.roundToInt

class VisibleMessageContentView : LinearLayout {
    private lateinit var binding:  ViewVisibleMessageContentBinding
    var onContentClick: MutableList<((event: MotionEvent) -> Unit)> = mutableListOf()
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

        val onlyBodyMessage = message is SmsMessageRecord
        val mediaThumbnailMessage = contactIsTrusted && message is MmsMessageRecord && message.slideDeck.thumbnailSlide != null

        // reset visibilities / containers
        onContentClick.clear()
        binding.albumThumbnailView.clearViews()
        onContentDoubleTap = null

        if (message.isDeleted) {
            binding.deletedMessageView.isVisible = true
            binding.deletedMessageView.bind(message, VisibleMessageContentView.getTextColor(context,message))
            return
        } else {
            binding.deletedMessageView.isVisible = false
        }

        binding.quoteView.isVisible = message is MmsMessageRecord && message.quote != null

        binding.linkPreviewView.isVisible = message is MmsMessageRecord && message.linkPreviews.isNotEmpty()

        val linkPreviewLayout = binding.linkPreviewView.layoutParams
        linkPreviewLayout.width = if (mediaThumbnailMessage) 0 else ViewGroup.LayoutParams.WRAP_CONTENT
        binding.linkPreviewView.layoutParams = linkPreviewLayout

        binding.untrustedView.isVisible = !contactIsTrusted && message is MmsMessageRecord && message.quote == null
        binding.voiceMessageView.isVisible = contactIsTrusted && message is MmsMessageRecord && message.slideDeck.audioSlide != null
        binding.documentView.isVisible = contactIsTrusted && message is MmsMessageRecord && message.slideDeck.documentSlide != null
        binding.albumThumbnailView.isVisible = mediaThumbnailMessage
        binding.openGroupInvitationView.isVisible = message.isOpenGroupInvitation

        var hideBody = false

        if (message is MmsMessageRecord && message.quote != null) {
            binding.quoteView.isVisible = true
            val quote = message.quote!!
            // The max content width is the max message bubble size - 2 times the horizontal padding - 2
            // times the horizontal margin. This unfortunately has to be calculated manually
            // here to get the layout right.
            val maxContentWidth = (maxWidth - 2 * resources.getDimension(R.dimen.medium_spacing) - 2 * toPx(16, resources)).roundToInt()
            val quoteText = if (quote.isOriginalMissing) {
                context.getString(R.string.QuoteView_original_missing)
            } else {
                quote.text
            }
            binding.quoteView.bind(quote.author.toString(), quoteText, quote.attachment, thread,
                message.isOutgoing, message.isOpenGroupInvitation, message.threadId,
                quote.isOriginalMissing, glide)
            onContentClick.add { event ->
                val r = Rect()
                binding.quoteView.getGlobalVisibleRect(r)
                if (r.contains(event.rawX.roundToInt(), event.rawY.roundToInt())) {
                    delegate?.scrollToMessageIfPossible(quote.id)
                }
            }
        }

        if (message is MmsMessageRecord && message.linkPreviews.isNotEmpty()) {
            binding.linkPreviewView.bind(message, glide, isStartOfMessageCluster, isEndOfMessageCluster)
            onContentClick.add { event -> binding.linkPreviewView.calculateHit(event) }
            // Body text view is inside the link preview for layout convenience
        } else if (message is MmsMessageRecord && message.slideDeck.audioSlide != null) {
            hideBody = true
            // Audio attachment
            if (contactIsTrusted || message.isOutgoing) {
                binding.voiceMessageView.indexInAdapter = indexInAdapter
                binding.voiceMessageView.delegate = context as? ConversationActivityV2
                binding.voiceMessageView.bind(message, isStartOfMessageCluster, isEndOfMessageCluster)
                // We have to use onContentClick (rather than a click listener directly on the voice
                // message view) so as to not interfere with all the other gestures.
                onContentClick.add { binding.voiceMessageView.togglePlayback() }
                onContentDoubleTap = { binding.voiceMessageView.handleDoubleTap() }
            } else {
                // TODO: move this out to its own area
                binding.untrustedView.bind(UntrustedAttachmentView.AttachmentType.AUDIO, VisibleMessageContentView.getTextColor(context,message))
                onContentClick.add { binding.untrustedView.showTrustDialog(message.individualRecipient) }
            }
        } else if (message is MmsMessageRecord && message.slideDeck.documentSlide != null) {
            hideBody = true
            // Document attachment
            if (contactIsTrusted || message.isOutgoing) {
                binding.documentView.bind(message, VisibleMessageContentView.getTextColor(context, message))
            } else {
                binding.untrustedView.bind(UntrustedAttachmentView.AttachmentType.DOCUMENT, VisibleMessageContentView.getTextColor(context,message))
                onContentClick.add { binding.untrustedView.showTrustDialog(message.individualRecipient) }
            }
        } else if (message is MmsMessageRecord && message.slideDeck.asAttachments().isNotEmpty()) {
            /*
             *    Images / Video attachment
             */
            if (contactIsTrusted || message.isOutgoing) {
                // isStart and isEnd of cluster needed for calculating the mask for full bubble image groups
                // bind after add view because views are inflated and calculated during bind
                binding.albumThumbnailView.bind(
                        glideRequests = glide,
                        message = message,
                        isStart = isStartOfMessageCluster,
                        isEnd = isEndOfMessageCluster
                )
                onContentClick.add { event ->
                    binding.albumThumbnailView.calculateHitObject(event, message, thread)
                }
            } else {
                hideBody = true
                binding.albumThumbnailView.clearViews()
                binding.untrustedView.bind(UntrustedAttachmentView.AttachmentType.MEDIA, VisibleMessageContentView.getTextColor(context,message))
                onContentClick.add { binding.untrustedView.showTrustDialog(message.individualRecipient) }
            }
        } else if (message.isOpenGroupInvitation) {
            hideBody = true
            binding.openGroupInvitationView.bind(message, VisibleMessageContentView.getTextColor(context, message))
            onContentClick.add { binding.openGroupInvitationView.joinOpenGroup() }
        }

        binding.bodyTextView.isVisible = message.body.isNotEmpty() && !hideBody

        // set it to use constraints if not only a text message, otherwise wrap content to whatever width it wants
        val params = binding.bodyTextView.layoutParams
        params.width = if (onlyBodyMessage || binding.barrierViewsGone()) ViewGroup.LayoutParams.WRAP_CONTENT else 0
        binding.bodyTextView.layoutParams = params
        binding.bodyTextView.maxWidth = maxWidth

        val bodyWidth = with (binding.bodyTextView) {
            StaticLayout.getDesiredWidth(text, paint).roundToInt()
        }

        val quote = (message as? MmsMessageRecord)?.quote
        val quoteLayoutParams = binding.quoteView.layoutParams
        quoteLayoutParams.width =
            if (mediaThumbnailMessage || quote == null) 0
            else binding.quoteView.calculateWidth(quote, bodyWidth, maxWidth, thread)

        binding.quoteView.layoutParams = quoteLayoutParams

        if (message.body.isNotEmpty() && !hideBody) {
            val color = getTextColor(context, message)
            binding.bodyTextView.setTextColor(color)
            binding.bodyTextView.setLinkTextColor(color)
            val body = getBodySpans(context, message, searchQuery)
            binding.bodyTextView.text = body
            onContentClick.add { e: MotionEvent ->
                binding.bodyTextView.getIntersectedModalSpans(e).iterator().forEach { span ->
                    span.onClick(binding.bodyTextView)
                }
            }
        }
    }

    private fun ViewVisibleMessageContentBinding.barrierViewsGone(): Boolean =
        listOf<View>(albumThumbnailView, linkPreviewView, voiceMessageView, quoteView).none { it.isVisible }

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
        arrayOf(
            binding.deletedMessageView,
            binding.untrustedView,
            binding.voiceMessageView,
            binding.openGroupInvitationView,
            binding.documentView,
            binding.quoteView,
            binding.linkPreviewView,
            binding.albumThumbnailView,
            binding.bodyTextView
        ).forEach { view -> view.isVisible = false }
    }

    fun playVoiceMessage() {
        binding.voiceMessageView.togglePlayback()
    }
    // endregion

    // region Convenience
    companion object {

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