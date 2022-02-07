package org.thoughtcrime.securesms.home.search

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import network.loki.messenger.R
import org.session.libsession.messaging.contacts.Contact
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.home.search.GlobalSearchAdapter.ContentView
import org.thoughtcrime.securesms.home.search.GlobalSearchAdapter.Model.GroupConversation
import org.thoughtcrime.securesms.home.search.GlobalSearchAdapter.Model.Message
import org.thoughtcrime.securesms.home.search.GlobalSearchAdapter.Model.SavedMessages
import org.thoughtcrime.securesms.util.DateUtils
import org.thoughtcrime.securesms.util.SearchUtil
import java.util.Locale
import org.thoughtcrime.securesms.home.search.GlobalSearchAdapter.Model.Contact as ContactModel


class GlobalSearchDiff(
    private val oldQuery: String?,
    private val newQuery: String?,
    private val oldData: List<GlobalSearchAdapter.Model>,
    private val newData: List<GlobalSearchAdapter.Model>
) : DiffUtil.Callback() {
    override fun getOldListSize(): Int = oldData.size
    override fun getNewListSize(): Int = newData.size
    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
        oldData[oldItemPosition] == newData[newItemPosition]

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
        oldQuery == newQuery && oldData[oldItemPosition] == newData[newItemPosition]

    override fun getChangePayload(oldItemPosition: Int, newItemPosition: Int): Any? =
        if (oldQuery != newQuery) newQuery
        else null
}

private val BoldStyleFactory = { StyleSpan(Typeface.BOLD) }

fun ContentView.bindQuery(query: String, model: GlobalSearchAdapter.Model) {
    when (model) {
        is ContactModel -> {
            binding.searchResultTitle.text = getHighlight(
                query,
                model.contact.getSearchName()
            )
        }
        is Message -> {
            val textSpannable = SpannableStringBuilder()
            if (model.messageResult.conversationRecipient != model.messageResult.messageRecipient) {
                // group chat, bind
                val text = "${model.messageResult.messageRecipient.getSearchName()}: "
                textSpannable.append(text)
            }
            textSpannable.append(getHighlight(
                    query,
                    model.messageResult.bodySnippet
            ))
            binding.searchResultSubtitle.text = textSpannable
            binding.searchResultSubtitle.isVisible = true
            binding.searchResultTitle.text = model.messageResult.conversationRecipient.toShortString()
        }
        is GroupConversation -> {
            binding.searchResultTitle.text = getHighlight(
                query,
                model.groupRecord.title
            )

            val membersString = model.groupRecord.members.joinToString { address ->
                val recipient = Recipient.from(binding.root.context, address, false)
                recipient.name ?: "${address.serialize().take(4)}...${address.serialize().takeLast(4)}"
            }
            binding.searchResultSubtitle.text = getHighlight(query, membersString)
        }
    }
}

private fun getHighlight(query: String?, toSearch: String): Spannable? {
    return SearchUtil.getHighlightedSpan(Locale.getDefault(), BoldStyleFactory, toSearch, query)
}

fun ContentView.bindModel(query: String?, model: GroupConversation) {
    binding.searchResultProfilePicture.isVisible = true
    binding.searchResultSavedMessages.isVisible = false
    binding.searchResultSubtitle.isVisible = model.groupRecord.isClosedGroup
    binding.searchResultTimestamp.isVisible = false
    val threadRecipient = Recipient.from(binding.root.context, Address.fromSerialized(model.groupRecord.encodedId), false)
    binding.searchResultProfilePicture.update(threadRecipient)
    val nameString = model.groupRecord.title
    binding.searchResultTitle.text = getHighlight(query, nameString)

    val groupRecipients = model.groupRecord.members.map { Recipient.from(binding.root.context, it, false) }

    val membersString = groupRecipients.joinToString {
        val address = it.address.serialize()
        it.name ?: "${address.take(4)}...${address.takeLast(4)}"
    }
    if (model.groupRecord.isClosedGroup) {
        binding.searchResultSubtitle.text = getHighlight(query, membersString)
    }
}

fun ContentView.bindModel(query: String?, model: ContactModel) {
    binding.searchResultProfilePicture.isVisible = true
    binding.searchResultSavedMessages.isVisible = false
    binding.searchResultSubtitle.isVisible = false
    binding.searchResultTimestamp.isVisible = false
    binding.searchResultSubtitle.text = null
    val recipient =
        Recipient.from(binding.root.context, Address.fromSerialized(model.contact.sessionID), false)
    binding.searchResultProfilePicture.update(recipient)
    val nameString = model.contact.getSearchName()
    binding.searchResultTitle.text = getHighlight(query, nameString)
}

fun ContentView.bindModel(model: SavedMessages) {
    binding.searchResultSubtitle.isVisible = false
    binding.searchResultTimestamp.isVisible = false
    binding.searchResultTitle.setText(R.string.note_to_self)
    binding.searchResultProfilePicture.isVisible = false
    binding.searchResultSavedMessages.isVisible = true
}

fun ContentView.bindModel(query: String?, model: Message) {
    binding.searchResultProfilePicture.isVisible = true
    binding.searchResultSavedMessages.isVisible = false
    binding.searchResultTimestamp.isVisible = true
//    val hasUnreads = model.unread > 0
//    binding.unreadCountIndicator.isVisible = hasUnreads
//    if (hasUnreads) {
//        binding.unreadCountTextView.text = model.unread.toString()
//    }
    binding.searchResultTimestamp.text = DateUtils.getDisplayFormattedTimeSpanString(binding.root.context, Locale.getDefault(), model.messageResult.receivedTimestampMs)
    binding.searchResultProfilePicture.update(model.messageResult.conversationRecipient)
    val textSpannable = SpannableStringBuilder()
    if (model.messageResult.conversationRecipient != model.messageResult.messageRecipient) {
        // group chat, bind
        val text = "${model.messageResult.messageRecipient.getSearchName()}: "
        textSpannable.append(text)
    }
    textSpannable.append(getHighlight(
            query,
            model.messageResult.bodySnippet
    ))
    binding.searchResultSubtitle.text = textSpannable
    binding.searchResultTitle.text = model.messageResult.conversationRecipient.toShortString()
    binding.searchResultSubtitle.isVisible = true
}

fun Recipient.getSearchName(): String = name ?: address.serialize().let { address -> "${address.take(4)}...${address.takeLast(4)}" }

fun Contact.getSearchName(): String =
        if (nickname.isNullOrEmpty()) name ?: "${sessionID.take(4)}...${sessionID.takeLast(4)}"
        else "${name ?: "${sessionID.take(4)}...${sessionID.takeLast(4)}"} ($nickname)"