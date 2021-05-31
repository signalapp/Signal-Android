package org.thoughtcrime.securesms.conversation.v2

import android.content.Context
import android.database.Cursor
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.thoughtcrime.securesms.conversation.v2.messages.MessageView
import org.thoughtcrime.securesms.database.CursorRecyclerViewAdapter
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.database.model.MessageRecord

class ConversationAdapter(context: Context, cursor: Cursor) : CursorRecyclerViewAdapter<ConversationAdapter.ViewHolder>(context, cursor) {
    private val messageDB = DatabaseFactory.getMmsSmsDatabase(context)

    class ViewHolder(val view: MessageView) : RecyclerView.ViewHolder(view)

    override fun onCreateItemViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = MessageView(context)
        return ViewHolder(view)
    }

    override fun onBindItemViewHolder(viewHolder: ViewHolder, cursor: Cursor) {
        val message = getMessage(cursor)!!
        viewHolder.view.bind(message)
    }

    override fun onItemViewRecycled(holder: ViewHolder?) {
        holder?.view?.recycle()
        super.onItemViewRecycled(holder)
    }

    private fun getMessage(cursor: Cursor): MessageRecord? {
        return messageDB.readerFor(cursor).current
    }
}