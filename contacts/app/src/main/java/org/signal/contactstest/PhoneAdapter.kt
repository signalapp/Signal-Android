package org.signal.contactstest

import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.signal.contacts.SystemContactsRepository

class PhoneAdapter(private val onContactClickedListener: (Uri) -> Unit) : ListAdapter<SystemContactsRepository.ContactPhoneDetails, PhoneAdapter.PhoneViewHolder>(object : DiffUtil.ItemCallback<SystemContactsRepository.ContactPhoneDetails>() {
  override fun areItemsTheSame(oldItem: SystemContactsRepository.ContactPhoneDetails, newItem: SystemContactsRepository.ContactPhoneDetails): Boolean {
    return oldItem == newItem
  }

  override fun areContentsTheSame(oldItem: SystemContactsRepository.ContactPhoneDetails, newItem: SystemContactsRepository.ContactPhoneDetails): Boolean {
    return oldItem == newItem
  }
}) {

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhoneViewHolder {
    return PhoneViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.child_item, parent, false))
  }

  override fun onBindViewHolder(holder: PhoneViewHolder, position: Int) {
    holder.bind(getItem(position))
  }

  inner class PhoneViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val photo: ImageView = itemView.findViewById(R.id.contact_photo)
    private val displayName: TextView = itemView.findViewById(R.id.display_name)
    private val number: TextView = itemView.findViewById(R.id.number)
    private val type: TextView = itemView.findViewById(R.id.type)
    private val goButton: View = itemView.findViewById(R.id.go_button)

    fun bind(details: SystemContactsRepository.ContactPhoneDetails) {
      if (details.photoUri != null) {
        photo.setImageBitmap(BitmapFactory.decodeStream(itemView.context.contentResolver.openInputStream(Uri.parse(details.photoUri))))
      } else {
        photo.setImageBitmap(null)
      }
      displayName.text = details.displayName
      number.text = details.number
      type.text = ContactsContract.CommonDataKinds.Phone.getTypeLabel(itemView.resources, details.type, details.label)
      goButton.setOnClickListener { onContactClickedListener(details.contactUri) }
    }
  }
}
