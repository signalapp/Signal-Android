package org.thoughtcrime.securesms.conversation.v2.messages

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import kotlinx.android.synthetic.main.view_document.view.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.database.model.MessageRecord

class DocumentView : LinearLayout {

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
        LayoutInflater.from(context).inflate(R.layout.view_document, this)
    }
    // endregion

    // region Updating
    fun bind(message: MessageRecord) {
        textView.text = "I'm a document"
    }

    fun recycle() {
        // TODO: Implement
    }
    // endregion
}