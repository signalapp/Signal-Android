package org.thoughtcrime.securesms.loki.redesign.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import network.loki.messenger.R
import org.thoughtcrime.securesms.database.model.ThreadRecord

class ConversationView : LinearLayout {

    // region Lifecycle
    companion object {

        fun get(context: Context, parent: ViewGroup?): ConversationView {
            return LayoutInflater.from(context).inflate(R.layout.conversation_view, parent, false) as ConversationView
        }
    }

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)
    // endregion

    // region Updating
    fun bind(thread: ThreadRecord) {

    }
    // endregion
}