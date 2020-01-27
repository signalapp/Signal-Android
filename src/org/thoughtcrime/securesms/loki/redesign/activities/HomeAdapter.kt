package org.thoughtcrime.securesms.loki.redesign.activities

import android.content.Context
import android.database.Cursor
import android.support.v7.widget.RecyclerView
import android.view.ViewGroup
import org.thoughtcrime.securesms.database.CursorRecyclerViewAdapter
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.database.model.ThreadRecord
import org.thoughtcrime.securesms.loki.redesign.views.ConversationView
import org.thoughtcrime.securesms.mms.GlideRequests

class HomeAdapter(context: Context, cursor: Cursor) : CursorRecyclerViewAdapter<HomeAdapter.ViewHolder>(context, cursor) {
    private val threadDatabase = DatabaseFactory.getThreadDatabase(context)
    lateinit var glide: GlideRequests
    var typingThreadIDs = setOf<Long>()
        set(value) { field = value; notifyDataSetChanged() }
    var conversationClickListener: ConversationClickListener? = null

    class ViewHolder(val view: ConversationView) : RecyclerView.ViewHolder(view)

    override fun onCreateItemViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = ConversationView(context)
        view.setOnClickListener { conversationClickListener?.onConversationClick(view) }
        view.setOnLongClickListener {
            conversationClickListener?.onLongConversationClick(view)
            true
        }
        return ViewHolder(view)
    }

    override fun onBindItemViewHolder(viewHolder: ViewHolder, cursor: Cursor) {
        val thread = getThread(cursor)!!
        val isTyping = typingThreadIDs.contains(thread.threadId)
        viewHolder.view.bind(thread, isTyping, glide)
    }

    private fun getThread(cursor: Cursor): ThreadRecord? {
        return threadDatabase.readerFor(cursor).current
    }
}

interface ConversationClickListener {
    fun onConversationClick(view: ConversationView)
    fun onLongConversationClick(view: ConversationView)
}