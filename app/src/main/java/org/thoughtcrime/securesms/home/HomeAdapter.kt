package org.thoughtcrime.securesms.home

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.NO_ID
import org.thoughtcrime.securesms.database.model.ThreadRecord
import org.thoughtcrime.securesms.mms.GlideRequests

class HomeAdapter(
    private val context: Context,
    private val listener: ConversationClickListener
) : RecyclerView.Adapter<RecyclerView.ViewHolder>(), ListUpdateCallback {

    companion object {
        private const val HEADER = 0
        private const val ITEM = 1
    }

    var header: View? = null

    private var _data: List<ThreadRecord> = emptyList()
    var data: List<ThreadRecord>
        get() = _data.toList()
        set(newData) {
            val previousData = _data.toList()
            val diff = HomeDiffUtil(previousData, newData, context)
            val diffResult = DiffUtil.calculateDiff(diff)
            _data = newData
            diffResult.dispatchUpdatesTo(this as ListUpdateCallback)
        }

    fun hasHeaderView(): Boolean = header != null

    private val headerCount: Int
        get() = if (header == null) 0 else 1

    override fun onInserted(position: Int, count: Int) {
        notifyItemRangeInserted(position + headerCount, count)
    }

    override fun onRemoved(position: Int, count: Int) {
        notifyItemRangeRemoved(position + headerCount, count)
    }

    override fun onMoved(fromPosition: Int, toPosition: Int) {
        notifyItemMoved(fromPosition + headerCount, toPosition + headerCount)
    }

    override fun onChanged(position: Int, count: Int, payload: Any?) {
        notifyItemRangeChanged(position + headerCount, count, payload)
    }

    override fun getItemId(position: Int): Long  {
        if (hasHeaderView() && position == 0) return NO_ID
        val offsetPosition = if (hasHeaderView()) position-1 else position
        return _data[offsetPosition].threadId
    }

    lateinit var glide: GlideRequests
    var typingThreadIDs = setOf<Long>()
        set(value) {
            field = value
            // TODO: replace this with a diffed update or a partial change set with payloads
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        when (viewType) {
            HEADER -> {
                HeaderFooterViewHolder(header!!)
            }
            ITEM -> {
                val view = ConversationView(context)
                view.setOnClickListener { view.thread?.let { listener.onConversationClick(it) } }
                view.setOnLongClickListener {
                    view.thread?.let { listener.onLongConversationClick(it) }
                    true
                }
                ViewHolder(view)
            }
            else -> throw Exception("viewType $viewType isn't valid")
        }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is ViewHolder) {
            val offset = if (hasHeaderView()) position - 1 else position
            val thread = data[offset]
            val isTyping = typingThreadIDs.contains(thread.threadId)
            holder.view.bind(thread, isTyping, glide)
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        if (holder is ViewHolder) {
            holder.view.recycle()
        } else {
            super.onViewRecycled(holder)
        }
    }

    override fun getItemViewType(position: Int): Int =
        if (hasHeaderView() && position == 0) HEADER
        else ITEM

    override fun getItemCount(): Int = data.size + if (hasHeaderView()) 1 else 0

    class ViewHolder(val view: ConversationView) : RecyclerView.ViewHolder(view)

    class HeaderFooterViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

}