package org.thoughtcrime.securesms.conversation.v2.messages

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import org.thoughtcrime.securesms.database.model.MessageRecord

class MessageView : LinearLayout {

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
        // TODO: Implement
    }
    // endregion

    // region Updating
    fun bind(message: MessageRecord) {

    }

    fun recycle() {
        // TODO: Implement
    }
    // endregion
}