package org.thoughtcrime.securesms.conversation.v2.messages

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.annotation.ColorInt
import kotlinx.android.synthetic.main.fragment_conversation_bottom_sheet.view.*
import kotlinx.android.synthetic.main.view_deleted_message.view.*
import kotlinx.android.synthetic.main.view_document.view.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import java.util.*

class DeletedMessageView : LinearLayout {

    // region Lifecycle
    constructor(context: Context) : super(context) { initialize() }
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) { initialize() }
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { initialize() }

    private fun initialize() {
        LayoutInflater.from(context).inflate(R.layout.view_deleted_message, this)
    }
    // endregion

    // region Updating
    fun bind(message: MessageRecord, @ColorInt textColor: Int) {
        assert(message.deleted)
        deleteTextView.text = context.getString(R.string.deleted_message)
        deleteTextView.setTextColor(textColor)
        deletedMessageViewIconImageView.imageTintList = ColorStateList.valueOf(textColor)
    }
    // endregion
}