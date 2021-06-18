package org.thoughtcrime.securesms.conversation.v2.messages

import android.content.Context
import android.content.res.Resources
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.RelativeLayout
import androidx.core.view.isVisible
import kotlinx.android.synthetic.main.view_quote.view.*
import network.loki.messenger.R
import org.session.libsession.messaging.contacts.Contact
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.conversation.v2.utilities.TextUtilities
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.loki.utilities.toPx
import org.thoughtcrime.securesms.mms.SlideDeck
import kotlin.math.max
import kotlin.math.min

class QuoteView : LinearLayout {
    private val screenWidth by lazy { Resources.getSystem().displayMetrics.widthPixels }
    private val vPadding by lazy { toPx(6, resources) }

    enum class Mode { Regular, Draft }

    // region Lifecycle
    constructor(context: Context) : super(context) { initialize() }
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) { initialize() }
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { initialize() }

    private fun initialize() {
        LayoutInflater.from(context).inflate(R.layout.view_quote, this)
    }
    // endregion

    // region General
    fun getIntrinsicContentHeight(): Int {
        var result = 0
        val width = screenWidth
        val author = quoteViewAuthorTextView.text
        val authorTextViewIntrinsicHeight = TextUtilities.getIntrinsicHeight(author, quoteViewAuthorTextView.paint, width)
        result += authorTextViewIntrinsicHeight
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
    fun bind(authorPublicKey: String, body: String, attachments: SlideDeck?, thread: Recipient) {
        val contactDB = DatabaseFactory.getSessionContactDatabase(context)
        // Author
        if (thread.isGroupRecipient) {
            val author = contactDB.getContactWithSessionID(authorPublicKey)
            val authorDisplayName = author?.displayName(Contact.contextForRecipient(thread)) ?: authorPublicKey
            quoteViewAuthorTextView.text = authorDisplayName
        }
        quoteViewAuthorTextView.isVisible = thread.isGroupRecipient
        // Body
        quoteViewBodyTextView.text = body
        // Accent line
        val accentLineLayoutParams = quoteViewAccentLine.layoutParams as RelativeLayout.LayoutParams
        accentLineLayoutParams.height = getIntrinsicContentHeight()
        quoteViewAccentLine.layoutParams = accentLineLayoutParams
    }
    // endregion
}