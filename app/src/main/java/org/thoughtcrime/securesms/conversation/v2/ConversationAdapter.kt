package org.thoughtcrime.securesms.conversation.v2

import android.content.Context
import android.database.Cursor
import android.graphics.Rect
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import kotlinx.android.synthetic.main.view_visible_message.view.*
import org.thoughtcrime.securesms.conversation.v2.messages.ControlMessageView
import org.thoughtcrime.securesms.conversation.v2.messages.VisibleMessageContentViewDelegate
import org.thoughtcrime.securesms.conversation.v2.messages.VisibleMessageView
import org.thoughtcrime.securesms.database.CursorRecyclerViewAdapter
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.mms.GlideRequests

class ConversationAdapter(context: Context, cursor: Cursor, private val onItemPress: (MessageRecord, Int, VisibleMessageView, MotionEvent) -> Unit,
    private val onItemSwipeToReply: (MessageRecord, Int) -> Unit, private val onItemLongPress: (MessageRecord, Int) -> Unit,
    private val glide: GlideRequests)
    : CursorRecyclerViewAdapter<ViewHolder>(context, cursor) {
    private val messageDB = DatabaseFactory.getMmsSmsDatabase(context)
    var selectedItems = mutableSetOf<MessageRecord>()
    private var searchQuery: String? = null
    var visibleMessageContentViewDelegate: VisibleMessageContentViewDelegate? = null

    sealed class ViewType(val rawValue: Int) {
        object Visible : ViewType(0)
        object Control : ViewType(1)

        companion object {

            val allValues: Map<Int, ViewType> get() = mapOf(
                Visible.rawValue to Visible,
                Control.rawValue to Control
            )
        }
    }

    class VisibleMessageViewHolder(val view: VisibleMessageView) : ViewHolder(view)
    class ControlMessageViewHolder(val view: ControlMessageView) : ViewHolder(view)

    override fun getItemViewType(cursor: Cursor): Int {
        val message = getMessage(cursor)!!
        if (message.isControlMessage) { return ViewType.Control.rawValue }
        return ViewType.Visible.rawValue
    }

    override fun onCreateItemViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        @Suppress("NAME_SHADOWING")
        val viewType = ViewType.allValues[viewType]
        when (viewType) {
            ViewType.Visible -> {
                val view = VisibleMessageView(context)
                return VisibleMessageViewHolder(view)
            }
            ViewType.Control -> {
                val view = ControlMessageView(context)
                return ControlMessageViewHolder(view)
            }
            else -> throw IllegalStateException("Unexpected view type: $viewType.")
        }
    }

    override fun onBindItemViewHolder(viewHolder: ViewHolder, cursor: Cursor) {
        val message = getMessage(cursor)!!
        when (viewHolder) {
            is VisibleMessageViewHolder -> {
                val view = viewHolder.view
                val isSelected = selectedItems.contains(message)
                view.snIsSelected = isSelected
                view.messageTimestampTextView.isVisible = isSelected
                val position = viewHolder.adapterPosition
                view.bind(message, getMessageBefore(position, cursor), getMessageAfter(position, cursor), glide, searchQuery)
                view.onPress = { event -> onItemPress(message, viewHolder.adapterPosition, view, event) }
                view.onSwipeToReply = { onItemSwipeToReply(message, viewHolder.adapterPosition) }
                view.onLongPress = { onItemLongPress(message, viewHolder.adapterPosition) }
                view.contentViewDelegate = visibleMessageContentViewDelegate
            }
            is ControlMessageViewHolder -> viewHolder.view.bind(message)
        }
    }

    override fun onItemViewRecycled(viewHolder: ViewHolder?) {
        when (viewHolder) {
            is VisibleMessageViewHolder -> viewHolder.view.recycle()
            is ControlMessageViewHolder -> viewHolder.view.recycle()
        }
        super.onItemViewRecycled(viewHolder)
    }

    private fun getMessage(cursor: Cursor): MessageRecord? {
        return messageDB.readerFor(cursor).current
    }

    private fun getMessageBefore(position: Int, cursor: Cursor): MessageRecord? {
        // The message that's visually before the current one is actually after the current
        // one for the cursor because the layout is reversed
        if (!cursor.moveToPosition(position + 1)) { return null }
        return messageDB.readerFor(cursor).current
    }

    private fun getMessageAfter(position: Int, cursor: Cursor): MessageRecord? {
        // The message that's visually after the current one is actually before the current
        // one for the cursor because the layout is reversed
        if (!cursor.moveToPosition(position - 1)) { return null }
        return messageDB.readerFor(cursor).current
    }

    fun toggleSelection(message: MessageRecord, position: Int) {
        if (selectedItems.contains(message)) selectedItems.remove(message) else selectedItems.add(message)
        notifyItemChanged(position)
    }

    fun findLastSeenItemPosition(lastSeenTimestamp: Long): Int? {
        val cursor = this.cursor
        if (lastSeenTimestamp <= 0L || cursor == null || !isActiveCursor) return null
        for (i in 0 until itemCount) {
            cursor.moveToPosition(i)
            val message = messageDB.readerFor(cursor).current
            if (message.isOutgoing || message.dateReceived <= lastSeenTimestamp) { return i }
        }
        return null
    }

    fun getItemPositionForTimestamp(timestamp: Long): Int? {
        val cursor = this.cursor
        if (timestamp <= 0L || cursor == null || !isActiveCursor) return null
        for (i in 0 until itemCount) {
            cursor.moveToPosition(i)
            val message = messageDB.readerFor(cursor).current
            if (message.dateSent == timestamp) { return i }
        }
        return null
    }

    fun onSearchQueryUpdated(query: String?) {
        this.searchQuery = query
        notifyDataSetChanged()
    }
}