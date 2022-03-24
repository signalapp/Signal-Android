package org.signal.contactstest

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.signal.contacts.SystemContactsRepository.ContactDetails
import org.signal.contacts.SystemContactsRepository.ContactPhoneDetails

class ContactsActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setContentView(R.layout.activity_contacts)

    val list: RecyclerView = findViewById(R.id.list)
    val adapter = ContactsAdapter()

    list.layoutManager = LinearLayoutManager(this)
    list.adapter = adapter

    val viewModel: ContactsViewModel by viewModels()
    viewModel.contacts.observe(this) { adapter.submitList(it) }
  }

  private inner class ContactsAdapter : ListAdapter<ContactDetails, ContactViewHolder>(object : DiffUtil.ItemCallback<ContactDetails>() {
    override fun areItemsTheSame(oldItem: ContactDetails, newItem: ContactDetails): Boolean {
      return oldItem == newItem
    }

    override fun areContentsTheSame(oldItem: ContactDetails, newItem: ContactDetails): Boolean {
      return oldItem == newItem
    }
  }) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
      return ContactViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.parent_item, parent, false))
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
      holder.bind(getItem(position))
    }
  }

  private inner class ContactViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val givenName: TextView = itemView.findViewById(R.id.given_name)
    val familyName: TextView = itemView.findViewById(R.id.family_name)
    val phoneAdapter: PhoneAdapter = PhoneAdapter()
    val phoneList: RecyclerView = itemView.findViewById<RecyclerView?>(R.id.phone_list).apply {
      layoutManager = LinearLayoutManager(itemView.context)
      adapter = phoneAdapter
    }

    fun bind(contact: ContactDetails) {
      givenName.text = "Given Name: ${contact.givenName}"
      familyName.text = "Family Name: ${contact.familyName}"
      phoneAdapter.submitList(contact.numbers)
    }
  }

  private inner class PhoneAdapter : ListAdapter<ContactPhoneDetails, PhoneViewHolder>(object : DiffUtil.ItemCallback<ContactPhoneDetails>() {
    override fun areItemsTheSame(oldItem: ContactPhoneDetails, newItem: ContactPhoneDetails): Boolean {
      return oldItem == newItem
    }

    override fun areContentsTheSame(oldItem: ContactPhoneDetails, newItem: ContactPhoneDetails): Boolean {
      return oldItem == newItem
    }
  }) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhoneViewHolder {
      return PhoneViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.child_item, parent, false))
    }

    override fun onBindViewHolder(holder: PhoneViewHolder, position: Int) {
      holder.bind(getItem(position))
    }
  }

  private inner class PhoneViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val photo: ImageView = itemView.findViewById(R.id.contact_photo)
    val displayName: TextView = itemView.findViewById(R.id.display_name)
    val number: TextView = itemView.findViewById(R.id.number)
    val type: TextView = itemView.findViewById(R.id.type)
    val goButton: View = itemView.findViewById(R.id.go_button)

    fun bind(details: ContactPhoneDetails) {
      if (details.photoUri != null) {
        photo.setImageBitmap(BitmapFactory.decodeStream(itemView.context.contentResolver.openInputStream(Uri.parse(details.photoUri))))
      } else {
        photo.setImageBitmap(null)
      }
      displayName.text = details.displayName
      number.text = details.number
      type.text = ContactsContract.CommonDataKinds.Phone.getTypeLabel(itemView.resources, details.type, details.label)
      goButton.setOnClickListener {
        startActivity(
          Intent(Intent.ACTION_VIEW).apply {
            data = details.contactUri
          }
        )
      }
    }
  }
}
