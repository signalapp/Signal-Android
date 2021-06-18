package org.thoughtcrime.securesms.conversation.v2.messages

import android.content.Context
import android.content.res.Resources
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.RelativeLayout
import kotlinx.android.synthetic.main.view_quote.view.*
import network.loki.messenger.R
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.conversation.v2.utilities.TextUtilities
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.loki.utilities.toPx
import org.thoughtcrime.securesms.mms.SlideDeck

class QuoteView : LinearLayout {
    private val screenWidth by lazy { Resources.getSystem().displayMetrics.widthPixels }

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
    fun getIntrinsicHeight(): Int {
        var result = 0
        val width = screenWidth
        val author = quoteViewAuthorTextView.text
        result += TextUtilities.getIntrinsicHeight(author, quoteViewAuthorTextView.paint, width)
        val body = quoteViewBodyTextView.text
        result += TextUtilities.getIntrinsicHeight(body, quoteViewBodyTextView.paint, width)
        return result
    }
    // endregion

    // region Updating
    fun bind(authorPublicKey: String, body: String, attachments: SlideDeck?, thread: Recipient) {
        val accentLineLayoutParams = quoteViewAccentLine.layoutParams as RelativeLayout.LayoutParams
        accentLineLayoutParams.height = getIntrinsicHeight()
        quoteViewAccentLine.layoutParams = accentLineLayoutParams
    }

    fun recycle() {
        // TODO: Implement
    }
    // endregion
}