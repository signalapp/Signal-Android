package org.thoughtcrime.securesms.home

import android.content.Context
import androidx.recyclerview.widget.DiffUtil
import org.thoughtcrime.securesms.database.model.ThreadRecord

class HomeDiffUtil(
    private val old: List<ThreadRecord>,
    private val new: List<ThreadRecord>,
    private val context: Context
): DiffUtil.Callback() {

    override fun getOldListSize(): Int = old.size

    override fun getNewListSize(): Int = new.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
        old[oldItemPosition].threadId == new[newItemPosition].threadId

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val oldItem = old[oldItemPosition]
        val newItem = new[newItemPosition]

        // return early to save getDisplayBody or expensive calls
        val sameCount = oldItem.count == newItem.count
        if (!sameCount) return false
        val sameUnreads = oldItem.unreadCount == newItem.unreadCount
        if (!sameUnreads) return false
        val samePinned = oldItem.isPinned == newItem.isPinned
        if (!samePinned) return false
        val sameRecipientHash = oldItem.recipientHash == newItem.recipientHash
        if (!sameRecipientHash) return false
        val sameSnippet = oldItem.getDisplayBody(context) == newItem.getDisplayBody(context)
        if (!sameSnippet) return false
        val sameSendStatus = oldItem.isFailed == newItem.isFailed && oldItem.isDelivered == newItem.isDelivered
                && oldItem.isSent == newItem.isSent && oldItem.isPending == newItem.isPending
        if (!sameSendStatus) return false

        // all same
        return true
    }

}