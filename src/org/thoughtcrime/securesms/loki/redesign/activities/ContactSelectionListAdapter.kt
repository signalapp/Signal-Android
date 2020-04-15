package org.thoughtcrime.securesms.loki.redesign.activities

import android.content.Context
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.contact_selection_list_divider.view.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.loki.redesign.views.UserView
import org.thoughtcrime.securesms.mms.GlideRequests
import org.thoughtcrime.securesms.recipients.Recipient

class ContactSelectionListAdapter(private val context: Context, private val isMulti: Boolean) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

  private object ViewType {
    const val Contact = 0
    const val Divider = 1
  }

  lateinit var glide: GlideRequests
  val selectedContacts = mutableSetOf<Recipient>()
  var items = listOf<ContactSelectionListLoaderItem>()
    set(value) { field = value; notifyDataSetChanged() }
  var contactClickListener: ContactClickListener? = null

  class ViewHolder(val view: UserView) : RecyclerView.ViewHolder(view)
  class DividerViewHolder(val view: View): RecyclerView.ViewHolder(view)

  override fun getItemCount(): Int {
    return items.size
  }

  override fun getItemViewType(position: Int): Int {
    return when (items[position]) {
      is ContactSelectionListLoaderItem.Header -> ViewType.Divider
      else -> ViewType.Contact
    }
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
    return if (viewType == ViewType.Contact) {
      ViewHolder(UserView(context))
    } else {
      val view = LayoutInflater.from(context).inflate(R.layout.contact_selection_list_divider, parent, false)
      DividerViewHolder(view)
    }
  }

  override fun onBindViewHolder(viewHolder: RecyclerView.ViewHolder, position: Int) {
    val item = items[position]
    if (viewHolder is ViewHolder) {
      item as ContactSelectionListLoaderItem.Contact
      viewHolder.view.setOnClickListener { contactClickListener?.onContactClick(item.recipient) }
      val isSelected = selectedContacts.contains(item.recipient)
      viewHolder.view.bind(item.recipient, isSelected, glide)
    } else if (viewHolder is DividerViewHolder) {
      item as ContactSelectionListLoaderItem.Header
      viewHolder.view.label.text = item.name
    }
  }

  fun onContactClick(recipient: Recipient) {
    if (selectedContacts.contains(recipient)) {
      selectedContacts.remove(recipient)
      contactClickListener?.onContactDeselected(recipient)
    } else {
      selectedContacts.add(recipient)
      contactClickListener?.onContactSelected(recipient)
    }
    val index = items.indexOfFirst {
      when (it) {
        is ContactSelectionListLoaderItem.Header -> false
        is ContactSelectionListLoaderItem.Contact -> it.recipient == recipient
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