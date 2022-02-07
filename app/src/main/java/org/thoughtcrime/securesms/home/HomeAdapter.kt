package org.thoughtcrime.securesms.home

import android.content.Context
import android.database.Cursor
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.thoughtcrime.securesms.database.CursorRecyclerViewAdapter
import org.thoughtcrime.securesms.database.model.ThreadRecord
import org.thoughtcrime.securesms.dependencies.DatabaseComponent
import org.thoughtcrime.securesms.mms.GlideRequests

class HomeAdapter(
    context: Context,
    cursor: Cursor?,
    val listener: ConversationClickListener
) : CursorRecyclerViewAdapter<HomeAdapter.ViewHolder>(context, cursor) {
    private val threadDatabase = DatabaseComponent.get(context).threadDatabase()
    lateinit var glide: GlideRequests
    var typingThreadIDs = setOf<Long>()
        set(value) { field = value; notifyDataSetChanged() }

    class ViewHolder(val view: ConversationView) : RecyclerView.ViewHolder(view)

    override fun onCreateItemViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = ConversationView(context)
        view.setOnClickListener { view.thread?.let { listener.onConversationClick(it) } }
        view.setOnLongClickListener {
            view.thread?.let { listener.onLongConversationClick(it) }
            true
        }
        return ViewHolder(view)
    }

    override fun onBindItemViewHolder(viewHolder: ViewHolder, cursor: Cursor) {
        val thread = getThread(cursor)!!
        val isTyping = typingThreadIDs.contains(thread.threadId)
        viewHolder.view.bind(thread, isTyping, glide)
    }

    override fun onItemViewRecycled(holder: ViewHolder?) {
        super.onItemViewRecycled(holder)
        holder?.view?.recycle()
    }

    private fun getThread(cursor: Cursor): ThreadRecord? {
        return threadDatabase.readerFor(cursor).current
    }
}

interface ConversationClickListener {
    fun onConversationClick(thread: ThreadRecord)
    fun onLongConversationClick(thread: ThreadRecord)
}