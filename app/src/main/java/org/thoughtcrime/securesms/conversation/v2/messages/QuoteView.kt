package org.thoughtcrime.securesms.conversation.v2.messages

import android.content.ContentResolver
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Resources
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.RelativeLayout
import androidx.annotation.ColorInt
import androidx.core.content.res.ResourcesCompat
import androidx.core.text.toSpannable
import androidx.core.view.isVisible
import androidx.core.view.marginStart
import com.google.android.exoplayer2.util.MimeTypes
import kotlinx.android.synthetic.main.view_link_preview.view.*
import kotlinx.android.synthetic.main.view_quote.view.*
import network.loki.messenger.R
import org.session.libsession.messaging.contacts.Contact
import org.session.libsession.messaging.utilities.UpdateMessageData
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.conversation.v2.utilities.TextUtilities
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.loki.utilities.*
import org.thoughtcrime.securesms.mms.GlideRequests
import org.thoughtcrime.securesms.mms.ImageSlide
import org.thoughtcrime.securesms.mms.SlideDeck
import org.thoughtcrime.securesms.util.MediaUtil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// There's quite some calculation going on here. It's a bit complex so don't make changes
// if you don't need to. If you do then test:
// • Quoted text in both private chats and group chats
// • Quoted images and videos in both private chats and group chats
// • Quoted voice messages and documents in both private chats and group chats
// • All of the above in both dark mode and light mode

class QuoteView : LinearLayout {
    private lateinit var mode: Mode
    private val vPadding by lazy { toPx(6, resources) }
    var delegate: QuoteViewDelegate? = null

    enum class Mode { Regular, Draft }

    // region Lifecycle
    constructor(context: Context) : super(context) { throw IllegalAccessError("Use QuoteView(context:mode:) instead.") }
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) { throw IllegalAccessError("Use QuoteView(context:mode:) instead.") }
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { throw IllegalAccessError("Use QuoteView(context:mode:) instead.") }

    constructor(context: Context, mode: Mode) : super(context) {
        this.mode = mode
        LayoutInflater.from(context).inflate(R.layout.view_quote, this)
        // Add padding here (not on mainQuoteViewContainer) to get a bit of a top inset while avoiding
        // the clipping issue described in getIntrinsicHeight(maxContentWidth:).
        setPadding(0, toPx(6, resources), 0, 0)
        when (mode) {
            Mode.Draft -> quoteViewCancelButton.setOnClickListener { delegate?.cancelQuoteDraft() }
            Mode.Regular -> {
                quoteViewCancelButton.isVisible = false
                mainQuoteViewContainer.setBackgroundColor(ResourcesCompat.getColor(resources, R.color.transparent, context.theme))
                val quoteViewMainContentContainerLayoutParams = quoteViewMainContentContainer.layoutParams as RelativeLayout.LayoutParams
                // Since we're not showing the cancel button we can shorten the end margin
                quoteViewMainContentContainerLayoutParams.marginEnd = resources.getDimension(R.dimen.medium_spacing).roundToInt()
                quoteViewMainContentContainer.layoutParams = quoteViewMainContentContainerLayoutParams
            }
        }
    }
    // endregion

    // region General
    fun getIntrinsicContentHeight(maxContentWidth: Int): Int {
        // If we're showing an attachment thumbnail, just constrain to the height of that
        if (quoteViewAttachmentPreviewContainer.isVisible) { return toPx(40, resources) }
        var result = 0
        var authorTextViewIntrinsicHeight = 0
        if (quoteViewAuthorTextView.isVisible) {
            val author = quoteViewAuthorTextView.text
            authorTextViewIntrinsicHeight = TextUtilities.getIntrinsicHeight(author, quoteViewAuthorTextView.paint, maxContentWidth)
            result += authorTextViewIntrinsicHeight
        }
        val body = quoteViewBodyTextView.text
        val bodyTextViewIntrinsicHeight = TextUtilities.getIntrinsicHeight(body, quoteViewBodyTextView.paint, maxContentWidth)
        result += bodyTextViewIntrinsicHeight
        if (!quoteViewAuthorTextView.isVisible) {
            // We want to at least be as high as the cancel button, and no higher than 56 DP (that's
            // approximately the height of 3 lines.
            return min(max(result, toPx(32, resources)), toPx(56, resources))
        } else {
            // Because we're showing the author text view, we should have a height of at least 32 DP
            // anyway, so there's no need to constrain to that. We constrain to a max height of 56 DP
            // because that's approximately the height of the author text view + 2 lines of the body
            // text view.
            return min(result, toPx(56, resources))
        }
    }

    fun getIntrinsicHeight(maxContentWidth: Int): Int {
        // The way all this works is that we just calculate the total height the quote view should be
        // and then center everything inside vertically. This effectively means we're applying padding.
        // Applying padding the regular way results in a clipping issue though due to a bug in
        // RelativeLayout.
        return getIntrinsicContentHeight(maxContentWidth) + 2 * vPadding
    }
    // endregion

    // region Updating
    fun bind(authorPublicKey: String, body: String?, attachments: SlideDeck?, thread: Recipient,
        isOutgoingMessage: Boolean, maxContentWidth: Int, isOpenGroupInvitation: Boolean, threadID: Long,
        isOriginalMissing: Boolean, glide: GlideRequests) {
        val contactDB = DatabaseFactory.getSessionContactDatabase(context)
        // Reduce the max body text view line count to 2 if this is a group thread because
        // we'll be showing the author text view and we don't want the overall quote view height
        // to get too big.
        quoteViewBodyTextView.maxLines = if (thread.isGroupRecipient) 2 else 3
        // Author
        if (thread.isGroupRecipient) {
            val author = contactDB.getContactWithSessionID(authorPublicKey)
            val authorDisplayName = author?.displayName(Contact.contextForRecipient(thread)) ?: authorPublicKey
            quoteViewAuthorTextView.text = authorDisplayName
            quoteViewAuthorTextView.setTextColor(getTextColor(isOutgoingMessage))
        }
        quoteViewAuthorTextView.isVisible = thread.isGroupRecipient
        // Body
        quoteViewBodyTextView.text = if (isOpenGroupInvitation) resources.getString(R.string.open_group_invitation_view__open_group_invitation) else MentionUtilities.highlightMentions((body ?: "").toSpannable(), threadID, context);
        quoteViewBodyTextView.setTextColor(getTextColor(isOutgoingMessage))
        // Accent line / attachment preview
        val hasAttachments = (attachments != null && attachments.asAttachments().isNotEmpty()) && !isOriginalMissing
        quoteViewAccentLine.isVisible = !hasAttachments
        quoteViewAttachmentPreviewContainer.isVisible = hasAttachments
        if (!hasAttachments) {
            val accentLineLayoutParams = quoteViewAccentLine.layoutParams as RelativeLayout.LayoutParams
            accentLineLayoutParams.height = getIntrinsicContentHeight(maxContentWidth) // Match the intrinsic * content * height
            quoteViewAccentLine.layoutParams = accentLineLayoutParams
            quoteViewAccentLine.setBackgroundColor(getLineColor(isOutgoingMessage))
        } else if (attachments != null) {
            quoteViewAttachmentPreviewImageView.imageTintList = ColorStateList.valueOf(ResourcesCompat.getColor(resources, R.color.white, context.theme))
            val backgroundColorID = if (UiModeUtilities.isDayUiMode(context)) R.color.black else R.color.accent
            val backgroundColor = ResourcesCompat.getColor(resources, backgroundColorID, context.theme)
            quoteViewAttachmentPreviewContainer.backgroundTintList = ColorStateList.valueOf(backgroundColor)
            quoteViewAttachmentPreviewImageView.isVisible = false
            quoteViewAttachmentThumbnailImageView.isVisible = false
            if (attachments.audioSlide != null) {
                quoteViewAttachmentPreviewImageView.setImageResource(R.drawable.ic_microphone)
                quoteViewAttachmentPreviewImageView.isVisible = true
                quoteViewBodyTextView.text = resources.getString(R.string.Slide_audio)
            } else if (attachments.documentSlide != null) {
                quoteViewAttachmentPreviewImageView.setImageResource(R.drawable.ic_document_large_light)
                quoteViewAttachmentPreviewImageView.isVisible = true
                quoteViewBodyTextView.text = resources.getString(R.string.document)
            } else if (attachments.thumbnailSlide != null) {
                val slide = attachments.thumbnailSlide!!
                // This internally fetches the thumbnail
                quoteViewAttachmentThumbnailImageView.radius = toPx(4, resources)
                quoteViewAttachmentThumbnailImageView.setImageResource(glide, slide, false, false)
                quoteViewAttachmentThumbnailImageView.isVisible = true
                quoteViewBodyTextView.text = if (MediaUtil.isVideo(slide.asAttachment())) resources.getString(R.string.Slide_video) else resources.getString(R.string.Slide_image)
            }
        }
        mainQuoteViewContainer.layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, getIntrinsicHeight(maxContentWidth))
        val quoteViewMainContentContainerLayoutParams = quoteViewMainContentContainer.layoutParams as RelativeLayout.LayoutParams
        // The start margin is different if we just show the accent line vs if we show an attachment thumbnail
        quoteViewMainContentContainerLayoutParams.marginStart = if (!hasAttachments) toPx(16, resources) else toPx(48, resources)
        quoteViewMainContentContainer.layoutParams = quoteViewMainContentContainerLayoutParams
    }
    // endregion

    // region Convenience
    @ColorInt private fun getLineColor(isOutgoingMessage: Boolean): Int {
        val isLightMode = UiModeUtilities.isDayUiMode(context)
        if ((mode == Mode.Regular && isLightMode) || (mode == Mode.Draft && isLightMode)) {
            return ResourcesCompat.getColor(resources, R.color.black, context.theme)
        } else if (mode == Mode.Regular && !isLightMode) {
            if (isOutgoingMessage) {
                return ResourcesCompat.getColor(resources, R.color.black, context.theme)
            } else {
                return ResourcesCompat.getColor(resources, R.color.accent, context.theme)
            }
        } else { // Draft & dark mode
            return ResourcesCompat.getColor(resources, R.color.accent, context.theme)
        }
    }

    @ColorInt private fun getTextColor(isOutgoingMessage: Boolean): Int {
        if (mode == Mode.Draft) { return ResourcesCompat.getColor(resources, R.color.text, context.theme) }
        val isLightMode = UiModeUtilities.isDayUiMode(context)
        if ((isOutgoingMessage && !isLightMode) || (!isOutgoingMessage && isLightMode)) {
            return ResourcesCompat.getColor(resources, R.color.black, context.theme)
        } else {
            return ResourcesCompat.getColor(resources, R.color.white, context.theme)
        }
    }
    // endregion
}

interface QuoteViewDelegate {

    fun cancelQuoteDraft()
}