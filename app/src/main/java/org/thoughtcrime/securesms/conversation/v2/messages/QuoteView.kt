package org.thoughtcrime.securesms.conversation.v2.messages

import android.content.Context
import android.content.res.Resources
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.RelativeLayout
import androidx.annotation.ColorInt
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import kotlinx.android.synthetic.main.view_quote.view.*
import network.loki.messenger.R
import org.session.libsession.messaging.contacts.Contact
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.conversation.v2.utilities.TextUtilities
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.loki.utilities.UiMode
import org.thoughtcrime.securesms.loki.utilities.UiModeUtilities
import org.thoughtcrime.securesms.loki.utilities.toPx
import org.thoughtcrime.securesms.mms.SlideDeck
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class QuoteView : LinearLayout {
    private lateinit var mode: Mode
    private val screenWidth by lazy { Resources.getSystem().displayMetrics.widthPixels }
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
        when (mode) {
            Mode.Draft -> quoteViewCancelButton.setOnClickListener { delegate?.cancelQuoteDraft() }
            Mode.Regular -> {
                quoteViewCancelButton.isVisible = false
                mainQuoteViewContainer.setBackgroundColor(ResourcesCompat.getColor(resources, R.color.transparent, context.theme))
                val hPadding = resources.getDimension(R.dimen.medium_spacing).roundToInt()
                mainQuoteViewContainer.setPadding(hPadding, toPx(12, resources), hPadding, toPx(0, resources))
                val quoteViewMainContentContainerLayoutParams = quoteViewMainContentContainer.layoutParams as RelativeLayout.LayoutParams
                quoteViewMainContentContainerLayoutParams.marginEnd = resources.getDimension(R.dimen.medium_spacing).roundToInt()
                quoteViewMainContentContainer.layoutParams = quoteViewMainContentContainerLayoutParams
            }
        }
    }
    // endregion

    // region General
    fun getIntrinsicContentHeight(): Int {
        var result = 0
        val width = screenWidth
        var authorTextViewIntrinsicHeight = 0
        if (quoteViewAuthorTextView.isVisible) {
            val author = quoteViewAuthorTextView.text
            authorTextViewIntrinsicHeight = TextUtilities.getIntrinsicHeight(author, quoteViewAuthorTextView.paint, width)
            result += authorTextViewIntrinsicHeight
        }
        val body = quoteViewBodyTextView.text
        val bodyTextViewIntrinsicHeight = TextUtilities.getIntrinsicHeight(body, quoteViewBodyTextView.paint, width)
        result += bodyTextViewIntrinsicHeight
        if (!quoteViewAuthorTextView.isVisible) {
            return min(max(result, toPx(32, resources)), toPx(54, resources))
        } else {
            return min(result, toPx(54, resources) + authorTextViewIntrinsicHeight)
        }
    }

    fun getIntrinsicHeight(): Int {
        return getIntrinsicContentHeight() + 2 * vPadding
    }
    // endregion

    // region Updating
    fun bind(authorPublicKey: String, body: String?, attachments: SlideDeck?, thread: Recipient, isOutgoingMessage: Boolean) {
        val contactDB = DatabaseFactory.getSessionContactDatabase(context)
        // Author
        if (thread.isGroupRecipient) {
            val author = contactDB.getContactWithSessionID(authorPublicKey)
            val authorDisplayName = author?.displayName(Contact.contextForRecipient(thread)) ?: authorPublicKey
            quoteViewAuthorTextView.text = authorDisplayName
            quoteViewAuthorTextView.setTextColor(getTextColor(isOutgoingMessage))
        }
        quoteViewAuthorTextView.isVisible = thread.isGroupRecipient
        // Body
        quoteViewBodyTextView.text = body
        quoteViewBodyTextView.setTextColor(getTextColor(isOutgoingMessage))
        // Accent line
        val accentLineLayoutParams = quoteViewAccentLine.layoutParams as RelativeLayout.LayoutParams
        accentLineLayoutParams.height = getIntrinsicContentHeight()
        quoteViewAccentLine.layoutParams = accentLineLayoutParams
        quoteViewAccentLine.setBackgroundColor(getLineColor(isOutgoingMessage))
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