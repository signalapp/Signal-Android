package org.thoughtcrime.securesms

import android.content.Context
import org.thoughtcrime.securesms.contacts.paged.ContactSearchAdapter
import org.thoughtcrime.securesms.contacts.paged.ContactSearchConfiguration
import org.thoughtcrime.securesms.contacts.paged.ContactSearchData
import org.thoughtcrime.securesms.util.adapter.mapping.LayoutFactory
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel

class ContactSelectionListAdapter(
  context: Context,
  displayCheckBox: Boolean,
  displaySmsTag: DisplaySmsTag,
  displayPhoneNumber: DisplayPhoneNumber,
  onClickCallbacks: OnContactSelectionClick,
  longClickCallbacks: LongClickCallbacks,
  storyContextMenuCallbacks: StoryContextMenuCallbacks
) : ContactSearchAdapter(context, emptySet(), displayCheckBox, displaySmsTag, displayPhoneNumber, onClickCallbacks, longClickCallbacks, storyContextMenuCallbacks) {

  init {
    registerFactory(NewGroupModel::class.java, LayoutFactory({ StaticMappingViewHolder(it, onClickCallbacks::onNewGroupClicked) }, R.layout.contact_selection_new_group_item))
    registerFactory(InviteToSignalModel::class.java, LayoutFactory({ StaticMappingViewHolder(it, onClickCallbacks::onInviteToSignalClicked) }, R.layout.contact_selection_invite_action_item))
  }

  class NewGroupModel : MappingModel<NewGroupModel> {
    override fun areItemsTheSame(newItem: NewGroupModel): Boolean = true
    override fun areContentsTheSame(newItem: NewGroupModel): Boolean = true
  }

  class InviteToSignalModel : MappingModel<InviteToSignalModel> {
    override fun areItemsTheSame(newItem: InviteToSignalModel): Boolean = true
    override fun areContentsTheSame(newItem: InviteToSignalModel): Boolean = true
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
