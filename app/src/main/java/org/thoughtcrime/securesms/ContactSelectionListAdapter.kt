package org.thoughtcrime.securesms

import android.content.Context
import android.view.View
import org.thoughtcrime.securesms.contacts.paged.ContactSearchAdapter
import org.thoughtcrime.securesms.contacts.paged.ContactSearchConfiguration
import org.thoughtcrime.securesms.contacts.paged.ContactSearchData
import org.thoughtcrime.securesms.contacts.paged.ContactSearchKey
import org.thoughtcrime.securesms.util.adapter.mapping.LayoutFactory
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel
import org.thoughtcrime.securesms.util.adapter.mapping.MappingViewHolder

class ContactSelectionListAdapter(
  context: Context,
  fixedContacts: Set<ContactSearchKey>,
  displayCheckBox: Boolean,
  displaySmsTag: DisplaySmsTag,
  displaySecondaryInformation: DisplaySecondaryInformation,
  onClickCallbacks: OnContactSelectionClick,
  longClickCallbacks: LongClickCallbacks,
  storyContextMenuCallbacks: StoryContextMenuCallbacks
) : ContactSearchAdapter(context, fixedContacts, displayCheckBox, displaySmsTag, displaySecondaryInformation, onClickCallbacks, longClickCallbacks, storyContextMenuCallbacks) {

  init {
    registerFactory(NewGroupModel::class.java, LayoutFactory({ NewGroupViewHolder(it, onClickCallbacks::onNewGroupClicked) }, R.layout.contact_selection_new_group_item))
    registerFactory(InviteToSignalModel::class.java, LayoutFactory({ InviteToSignalViewHolder(it, onClickCallbacks::onInviteToSignalClicked) }, R.layout.contact_selection_invite_action_item))
  }

  class NewGroupModel : MappingModel<NewGroupModel> {
    override fun areItemsTheSame(newItem: NewGroupModel): Boolean = true
    override fun areContentsTheSame(newItem: NewGroupModel): Boolean = true
  }

  class InviteToSignalModel : MappingModel<InviteToSignalModel> {
    override fun areItemsTheSame(newItem: InviteToSignalModel): Boolean = true
    override fun areContentsTheSame(newItem: InviteToSignalModel): Boolean = true
  }

  private class InviteToSignalViewHolder(itemView: View, onClickListener: () -> Unit) : MappingViewHolder<InviteToSignalModel>(itemView) {
    init {
      itemView.setOnClickListener { onClickListener() }
    }

    override fun bind(model: InviteToSignalModel) = Unit
  }

  private class NewGroupViewHolder(itemView: View, onClickListener: () -> Unit) : MappingViewHolder<NewGroupModel>(itemView) {
    init {
      itemView.setOnClickListener { onClickListener() }
    }

    override fun bind(model: NewGroupModel) = Unit
  }

  class ArbitraryRepository : org.thoughtcrime.securesms.contacts.paged.ArbitraryRepository {

    enum class ArbitraryRow(val code: String) {
      NEW_GROUP("new-group"),
      INVITE_TO_SIGNAL("invite-to-signal");

      companion object {
        fun fromCode(code: String) = values().first { it.code == code }
      }
    }

    override fun getSize(section: ContactSearchConfiguration.Section.Arbitrary, query: String?): Int {
      return if (query.isNullOrEmpty()) section.types.size else 0
    }

    override fun getData(section: ContactSearchConfiguration.Section.Arbitrary, query: String?, startIndex: Int, endIndex: Int, totalSearchSize: Int): List<ContactSearchData.Arbitrary> {
      check(section.types.size == 1)
      return listOf(ContactSearchData.Arbitrary(section.types.first()))
    }

    override fun getMappingModel(arbitrary: ContactSearchData.Arbitrary): MappingModel<*> {
      val code = ArbitraryRow.fromCode(arbitrary.type)
      return when (code) {
        ArbitraryRow.NEW_GROUP -> NewGroupModel()
        ArbitraryRow.INVITE_TO_SIGNAL -> InviteToSignalModel()
      }
    }
  }

  interface OnContactSelectionClick : ClickCallbacks {
    fun onNewGroupClicked()
    fun onInviteToSignalClicked()
  }
}
