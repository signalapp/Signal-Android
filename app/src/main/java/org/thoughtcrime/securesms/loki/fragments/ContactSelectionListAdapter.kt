package org.thoughtcrime.securesms.loki.fragments

import android.content.Context
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.contact_selection_list_divider.view.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.loki.views.UserView
import org.thoughtcrime.securesms.mms.GlideRequests
import org.session.libsession.utilities.recipients.Recipient

class ContactSelectionListAdapter(private val context: Context, private val multiSelect: Boolean) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    lateinit var glide: GlideRequests
    val selectedContacts = mutableSetOf<Recipient>()
    var items = listOf<ContactSelectionListItem>()
        set(value) { field = value; notifyDataSetChanged() }
    var contactClickListener: ContactClickListener? = null

    private object ViewType {
        const val Contact = 0
        const val Divider = 1
    }

    class UserViewHolder(val view: UserView) : RecyclerView.ViewHolder(view)
    class DividerViewHolder(val view: View) : RecyclerView.ViewHolder(view)

    override fun getItemCount(): Int {
        return items.size
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is ContactSelectionListItem.Header -> ViewType.Divider
            else -> ViewType.Contact
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == ViewType.Contact) {
          UserViewHolder(UserView(context))
        } else {
            val view = LayoutInflater.from(context).inflate(R.layout.contact_selection_list_divider, parent, false)
          DividerViewHolder(view)
        }
    }

    override fun onBindViewHolder(viewHolder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        if (viewHolder is UserViewHolder) {
            item as ContactSelectionListItem.Contact
            viewHolder.view.setOnClickListener { contactClickListener?.onContactClick(item.recipient) }
            val isSelected = selectedContacts.contains(item.recipient)
            viewHolder.view.bind(
                    item.recipient,
                    glide,
                    if (multiSelect) UserView.ActionIndicator.Tick else UserView.ActionIndicator.None,
                    isSelected)
        } else if (viewHolder is DividerViewHolder) {
            item as ContactSelectionListItem.Header
            viewHolder.view.label.text = item.name
        }
    }

    fun onContactClick(recipient: Recipient) {
        if (selectedContacts.contains(recipient)) {
            selectedContacts.remove(recipient)
            contactClickListener?.onContactDeselected(recipient)
        } else if (multiSelect || selectedContacts.isEmpty()) {
            selectedContacts.add(recipient)
            contactClickListener?.onContactSelected(recipient)
        }
        val index = items.indexOfFirst {
            when (it) {
                is ContactSelectionListItem.Header -> false
                is ContactSelectionListItem.Contact -> it.recipient == recipient
            }
        }
        notifyItemChanged(index)
    }
}

interface ContactClickListener {
    fun onContactClick(contact: Recipient)
    fun onContactSelected(contact: Recipient)
    fun onContactDeselected(contact: Recipient)
}