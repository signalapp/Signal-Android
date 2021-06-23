package org.thoughtcrime.securesms.conversation.v2.messages

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
import androidx.core.view.isVisible
import androidx.core.view.marginStart
import kotlinx.android.synthetic.main.view_quote.view.*
import network.loki.messenger.R
import org.session.libsession.messaging.contacts.Contact
import org.session.libsession.messaging.utilities.UpdateMessageData
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.conversation.v2.utilities.TextUtilities
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.loki.utilities.UiMode
import org.thoughtcrime.securesms.loki.utilities.UiModeUtilities
import org.thoughtcrime.securesms.loki.utilities.toDp
import org.thoughtcrime.securesms.loki.utilities.toPx
import org.thoughtcrime.securesms.mms.SlideDeck
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

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
        setPadding(0, toPx(6, resources), 0, 0)
        when (mode) {
            Mode.Draft -> quoteViewCancelButton.setOnClickListener { delegate?.cancelQuoteDraft() }
            Mode.Regular -> {
                quoteViewCancelButton.isVisible = false
                mainQuoteViewContainer.setBackgroundColor(ResourcesCompat.getColor(resources, R.color.transparent, context.theme))
                val quoteViewMainContentContainerLayoutParams = quoteViewMainContentContainer.layoutParams as RelativeLayout.LayoutParams
                quoteViewMainContentContainerLayoutParams.marginEnd = resources.getDimension(R.dimen.medium_spacing).roundToInt()
                quoteViewMainContentContainer.layoutParams = quoteViewMainContentContainerLayoutParams
            }
        }
    }
    // endregion

    // region General
    fun getIntrinsicContentHeight(maxContentWidth: Int): Int {
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
            return min(max(result, toPx(32, resources)), toPx(56, resources))
        } else {
            return min(result, toPx(56, resources))
        }
    }

    fun getIntrinsicHeight(maxContentWidth: Int): Int {
        var result = getIntrinsicContentHeight(maxContentWidth)
        result += 2 * vPadding
        return result
    }
    // endregion

    // region Updating
    fun bind(authorPublicKey: String, body: String?, attachments: SlideDeck?, thread: Recipient,
        isOutgoingMessage: Boolean, maxContentWidth: Int, isOpenGroupInvitation: Boolean) {
        val contactDB = DatabaseFactory.getSessionContactDatabase(context)
        quoteViewBodyTextView.maxLines = 3
        // Author
        if (thread.isGroupRecipient) {
            val author = contactDB.getContactWithSessionID(authorPublicKey)
            val authorDisplayName = author?.displayName(Contact.contextForRecipient(thread)) ?: authorPublicKey
            quoteViewAuthorTextView.text = authorDisplayName
            quoteViewAuthorTextView.setTextColor(getTextColor(isOutgoingMessage))
            quoteViewBodyTextView.maxLines = 2
        }
        quoteViewAuthorTextView.isVisible = thread.isGroupRecipient
        // Body
        quoteViewBodyTextView.text = if (isOpenGroupInvitation) resources.getString(R.string.open_group_invitation_view__open_group_invitation) else body
        quoteViewBodyTextView.setTextColor(getTextColor(isOutgoingMessage))
        // Accent line / attachment preview
        val hasAttachments = (attachments != null && attachments.asAttachments().isNotEmpty())
        quoteViewAccentLine.isVisible = !hasAttachments
        quoteViewAttachmentPreviewContainer.isVisible = hasAttachments
        if (!hasAttachments) {
            val accentLineLayoutParams = quoteViewAccentLine.layoutParams as RelativeLayout.LayoutParams
            accentLineLayoutParams.height = getIntrinsicContentHeight(maxContentWidth)
            quoteViewAccentLine.layoutParams = accentLineLayoutParams
            quoteViewAccentLine.setBackgroundColor(getLineColor(isOutgoingMessage))
        } else {
            attachments!!
            quoteViewAttachmentPreviewImageView.imageTintList = ColorStateList.valueOf(ResourcesCompat.getColor(resources, R.color.white, context.theme))
            val backgroundColorID = if (UiModeUtilities.isDayUiMode(context)) R.color.black else R.color.accent
            val backgroundColor = ResourcesCompat.getColor(resources, backgroundColorID, context.theme)
            quoteViewAttachmentPreviewContainer.backgroundTintList = ColorStateList.valueOf(backgroundColor)
            if (attachments.audioSlide != null) {
                quoteViewAttachmentPreviewImageView.setImageResource(R.drawable.ic_microphone)
                quoteViewBodyTextView.text = resources.getString(R.string.Slide_audio)
            } else if (attachments.documentSlide != null) {
                quoteViewAttachmentPreviewImageView.setImageResource(R.drawable.ic_document_large_light)
                quoteViewBodyTextView.text = resources.getString(R.string.document)
            }
            // TODO: Link previews
            // TODO: Images/video
        }
        mainQuoteViewContainer.layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, getIntrinsicHeight(maxContentWidth))
        val quoteViewMainContentContainerLayoutParams = quoteViewMainContentContainer.layoutParams as RelativeLayout.LayoutParams
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