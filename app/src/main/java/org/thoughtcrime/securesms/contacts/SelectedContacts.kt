package org.thoughtcrime.securesms.contacts

import android.content.res.ColorStateList
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.contacts.paged.ChatType
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.adapter.mapping.LayoutFactory
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel
import org.thoughtcrime.securesms.util.adapter.mapping.MappingViewHolder

object SelectedContacts {
  @JvmStatic
  fun register(adapter: MappingAdapter, onCloseIconClicked: (Model<*>) -> Unit) {
    adapter.registerFactory(RecipientModel::class.java, LayoutFactory({ RecipientViewHolder(it, onCloseIconClicked) }, R.layout.contact_selection_list_chip))
    adapter.registerFactory(ChatTypeModel::class.java, LayoutFactory({ ChatTypeViewHolder(it, onCloseIconClicked) }, R.layout.contact_selection_list_chip))
  }

  sealed class Model<T : Any>(val selectedContact: SelectedContact) : MappingModel<T>

  class RecipientModel(selectedContact: SelectedContact, val recipient: Recipient) : Model<RecipientModel>(selectedContact = selectedContact) {
    override fun areItemsTheSame(newItem: RecipientModel): Boolean {
      return newItem.selectedContact.matches(selectedContact) && recipient == newItem.recipient
    }

    override fun areContentsTheSame(newItem: RecipientModel): Boolean {
      return areItemsTheSame(newItem) && recipient.hasSameContent(newItem.recipient)
    }
  }

  private class RecipientViewHolder(itemView: View, private val onCloseIconClicked: (RecipientModel) -> Unit) : MappingViewHolder<RecipientModel>(itemView) {

    private val chip: ContactChip = itemView.findViewById(R.id.contact_chip)

    override fun bind(model: RecipientModel) {
      chip.text = if (model.recipient.isSelf) context.getString(R.string.note_to_self) else model.recipient.getShortDisplayName(context)
      chip.setContact(model.selectedContact)
      chip.isCloseIconVisible = true
      chip.setOnCloseIconClickListener {
        onCloseIconClicked(model)
      }
      chip.setAvatar(Glide.with(itemView), model.recipient, null)
    }
  }

  class ChatTypeModel(selectedContact: SelectedContact) : Model<ChatTypeModel>(selectedContact = selectedContact) {
    override fun areItemsTheSame(newItem: ChatTypeModel): Boolean {
      return newItem.selectedContact.matches(selectedContact) && newItem.selectedContact.chatType == selectedContact.chatType
    }

    override fun areContentsTheSame(newItem: ChatTypeModel): Boolean {
      return areItemsTheSame(newItem)
    }
  }

  private class ChatTypeViewHolder(itemView: View, private val onCloseIconClicked: (ChatTypeModel) -> Unit) : MappingViewHolder<ChatTypeModel>(itemView) {

    private val chip: ContactChip = itemView.findViewById(R.id.contact_chip)

    override fun bind(model: ChatTypeModel) {
      if (model.selectedContact.chatType == ChatType.INDIVIDUAL) {
        chip.text = context.getString(R.string.ChatFoldersFragment__one_on_one_chats)
        chip.chipIcon = AppCompatResources.getDrawable(context, R.drawable.symbol_person_light_24)
        chip.chipIconTint = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.signal_colorOnSurface))
      } else {
        chip.text = context.getString(R.string.ChatFoldersFragment__groups)
        chip.chipIcon = AppCompatResources.getDrawable(context, R.drawable.symbol_group_light_20)
        chip.chipIconTint = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.signal_colorOnSurface))
      }
      chip.setContact(model.selectedContact)
      chip.isCloseIconVisible = true
      chip.setOnCloseIconClickListener {
        onCloseIconClicked(model)
      }
    }
  }
}
