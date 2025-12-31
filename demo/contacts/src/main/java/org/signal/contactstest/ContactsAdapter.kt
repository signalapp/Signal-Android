package org.signal.contactstest

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.signal.contacts.SystemContactsRepository

class ContactsAdapter(private val onContactClickedListener: (Uri) -> Unit) : ListAdapter<SystemContactsRepository.ContactDetails, ContactsAdapter.ContactViewHolder>(object : DiffUtil.ItemCallback<SystemContactsRepository.ContactDetails>() {
  override fun areItemsTheSame(oldItem: SystemContactsRepository.ContactDetails, newItem: SystemContactsRepository.ContactDetails): Boolean {
    return oldItem == newItem
  }

  override fun areContentsTheSame(oldItem: SystemContactsRepository.ContactDetails, newItem: SystemContactsRepository.ContactDetails): Boolean {
    return oldItem == newItem
  }
}) {

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
    return ContactViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.parent_item, parent, false))
  }

  override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
    holder.bind(getItem(position))
  }

  inner class ContactViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val givenName: TextView = itemView.findViewById(R.id.given_name)
    private val familyName: TextView = itemView.findViewById(R.id.family_name)
    private val phoneAdapter: PhoneAdapter = PhoneAdapter(onContactClickedListener)

    init {
      itemView.findViewById<RecyclerView?>(R.id.phone_list).apply {
        layoutManager = LinearLayoutManager(itemView.context)
        adapter = phoneAdapter
      }
    }

    fun bind(contact: SystemContactsRepository.ContactDetails) {
      givenName.text = "Given Name: ${contact.givenName}"
      familyName.text = "Family Name: ${contact.familyName}"
      phoneAdapter.submitList(contact.numbers)
    }
  }
}
