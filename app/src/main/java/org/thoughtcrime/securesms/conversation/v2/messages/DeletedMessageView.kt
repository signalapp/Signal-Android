package org.thoughtcrime.securesms.conversation.v2.messages

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.widget.LinearLayout
import androidx.annotation.ColorInt
import network.loki.messenger.R
import network.loki.messenger.databinding.ViewDeletedMessageBinding
import org.thoughtcrime.securesms.database.model.MessageRecord

class DeletedMessageView : LinearLayout {
    private val binding: ViewDeletedMessageBinding by lazy { ViewDeletedMessageBinding.bind(this) }
    // region Lifecycle
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    // endregion

    // region Updating
    fun bind(message: MessageRecord, @ColorInt textColor: Int) {
        assert(message.isDeleted)
        binding.deleteTitleTextView.text = context.getString(R.string.deleted_message)
        binding.deleteTitleTextView.setTextColor(textColor)
        binding.deletedMessageViewIconImageView.imageTintList = ColorStateList.valueOf(textColor)
    }
    // endregion
}