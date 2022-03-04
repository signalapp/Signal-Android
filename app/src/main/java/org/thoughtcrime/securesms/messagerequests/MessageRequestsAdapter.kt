package org.thoughtcrime.securesms.messagerequests

import android.annotation.SuppressLint
import android.content.Context
import android.database.Cursor
import android.os.Build
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import network.loki.messenger.R
import org.thoughtcrime.securesms.database.CursorRecyclerViewAdapter
import org.thoughtcrime.securesms.database.model.ThreadRecord
import org.thoughtcrime.securesms.dependencies.DatabaseComponent
import org.thoughtcrime.securesms.mms.GlideRequests


class MessageRequestsAdapter(
    context: Context,
    cursor: Cursor?,
    val listener: ConversationClickListener
) : CursorRecyclerViewAdapter<MessageRequestsAdapter.ViewHolder>(context, cursor) {
    private val threadDatabase = DatabaseComponent.get(context).threadDatabase()
    lateinit var glide: GlideRequests

    class ViewHolder(val view: MessageRequestView) : RecyclerView.ViewHolder(view)

    override fun onCreateItemViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = MessageRequestView(context)
        view.setOnClickListener { view.thread?.let { listener.onConversationClick(it) } }
        view.setOnLongClickListener {
            view.thread?.let { showPopupMenu(view) }
            true
        }
        return ViewHolder(view)
    }

    override fun onBindItemViewHolder(viewHolder: ViewHolder, cursor: Cursor) {
        val thread = getThread(cursor)!!
        viewHolder.view.bind(thread, glide)
    }

    override fun onItemViewRecycled(holder: ViewHolder?) {
        super.onItemViewRecycled(holder)
        holder?.view?.recycle()
    }

    private fun showPopupMenu(view: MessageRequestView) {
        val popupMenu = PopupMenu(context, view)
        popupMenu.menuInflater.inflate(R.menu.menu_message_request, popupMenu.menu)
        popupMenu.setOnMenuItemClickListener { menuItem ->
            if (menuItem.itemId == R.id.menu_delete_message_request) {
                listener.onLongConversationClick(view.thread!!)
            }
            true
        }
        for (i in 0 until popupMenu.menu.size()) {
            val item = popupMenu.menu.getItem(i)
            val s = SpannableString(item.title)
            s.setSpan(ForegroundColorSpan(context.getColor(R.color.destructive)), 0, s.length, 0)
            item.title = s
        }
        popupMenu.forceShowIcon() //TODO: call setForceShowIcon(true) after update to appcompat 1.4.1+
        popupMenu.show()
    }

    private fun getThread(cursor: Cursor): ThreadRecord? {
        return threadDatabase.readerFor(cursor).current
    }
}

interface ConversationClickListener {
    fun onConversationClick(thread: ThreadRecord)
    fun onLongConversationClick(thread: ThreadRecord)
}

@SuppressLint("PrivateApi")
private fun PopupMenu.forceShowIcon() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        this.setForceShowIcon(true)
    } else {
        try {
            val popupField = PopupMenu::class.java.getDeclaredField("mPopup")
            popupField.isAccessible = true
            val menu = popupField.get(this)
            menu.javaClass.getDeclaredMethod("setForceShowIcon", Boolean::class.java)
                .invoke(menu, true)
        } catch (exception: Exception) {
            Log.d("Loki", "Couldn't show message request popupmenu due to error: $exception.")
        }
    }
}
