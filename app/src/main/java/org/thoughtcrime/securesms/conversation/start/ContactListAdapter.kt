package org.thoughtcrime.securesms.conversation.start

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import network.loki.messenger.databinding.ContactSectionHeaderBinding
import network.loki.messenger.databinding.ViewContactBinding
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.mms.GlideRequests

sealed class ContactListItem {
    class Header(val name: String) : ContactListItem()
    class Contact(val recipient: Recipient, val displayName: String) : ContactListItem()
}

class ContactListAdapter(
    private val context: Context,
    private val glide: GlideRequests,
    private val listener: (Recipient) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    var items = listOf<ContactListItem>()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    private object ViewType {
        const val Contact = 0
        const val Header = 1
    }

    class ContactViewHolder(private val binding: ViewContactBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(contact: ContactListItem.Contact, glide: GlideRequests, listener: (Recipient) -> Unit) {
            binding.profilePictureView.root.glide = glide
            binding.profilePictureView.root.update(contact.recipient)
            binding.nameTextView.text = contact.displayName
            binding.root.setOnClickListener { listener(contact.recipient) }
        }

        fun unbind() {
            binding.profilePictureView.root.recycle()
        }
    }

    class HeaderViewHolder(
        private val binding: ContactSectionHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ContactListItem.Header) {
            with(binding) {
                label.text = item.name
            }
        }
    }

    override fun getItemCount(): Int {
        return items.size
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is ContactViewHolder) {
            holder.unbind()
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is ContactListItem.Header -> ViewType.Header
            else -> ViewType.Contact
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == ViewType.Contact) {
            ContactViewHolder(
                ViewContactBinding.inflate(LayoutInflater.from(context), parent, false)
            )
        } else {
            HeaderViewHolder(
                ContactSectionHeaderBinding.inflate(LayoutInflater.from(context), parent, false)
            )
        }
    }

    override fun onBindViewHolder(viewHolder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        if (viewHolder is ContactViewHolder) {
            viewHolder.bind(item as ContactListItem.Contact, glide, listener)
        } else if (viewHolder is HeaderViewHolder) {
            viewHolder.bind(item as ContactListItem.Header)
        }
    }

}
