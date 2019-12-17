package org.thoughtcrime.securesms.loki.redesign.activities

import android.content.Context
import android.database.Cursor
import android.support.v7.widget.RecyclerView
import android.view.ViewGroup
import org.thoughtcrime.securesms.database.CursorRecyclerViewAdapter
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.database.model.ThreadRecord
import org.thoughtcrime.securesms.loki.redesign.views.ConversationView

class ConversationAdapter(context: Context, cursor: Cursor) : CursorRecyclerViewAdapter<ConversationAdapter.ViewHolder>(context, cursor) {
    private val threadDatabase = DatabaseFactory.getThreadDatabase(context)
    var conversationClickListener: ConversationClickListener? = null

    class ViewHolder(val view: ConversationView) : RecyclerView.ViewHolder(view)

    override fun onCreateItemViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = ConversationView.get(context, parent)
        view.setOnClickListener { conversationClickListener?.onConversationClick(view) }
        view.setOnLongClickListener {
            conversationClickListener?.onLongConversationClick(view)
            true
        }
        return ViewHolder(view)
    }

    override fun onBindItemViewHolder(viewHolder: ViewHolder, cursor: Cursor) {
        viewHolder.view.bind(getThread(cursor)!!)
    }

    private fun getThread(cursor: Cursor): ThreadRecord? {
        return threadDatabase.readerFor(cursor).getCurrent()
    }
}

interface ConversationClickListener {
    fun onConversationClick(view: ConversationView)
    fun onLongConversationClick(view: ConversationView)
}