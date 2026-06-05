package org.thoughtcrime.securesms

import android.content.Context
import org.thoughtcrime.securesms.ContactSelectionListModels.FindByPhoneNumberModel
import org.thoughtcrime.securesms.ContactSelectionListModels.FindByUsernameModel
import org.thoughtcrime.securesms.ContactSelectionListModels.FindContactsBannerModel
import org.thoughtcrime.securesms.ContactSelectionListModels.FindContactsModel
import org.thoughtcrime.securesms.ContactSelectionListModels.InviteToSignalModel
import org.thoughtcrime.securesms.ContactSelectionListModels.MoreHeaderModel
import org.thoughtcrime.securesms.ContactSelectionListModels.NewGroupModel
import org.thoughtcrime.securesms.ContactSelectionListModels.RefreshContactsModel
import org.thoughtcrime.securesms.contacts.paged.ContactSearchAdapter
import org.thoughtcrime.securesms.contacts.paged.ContactSearchConfiguration
import org.thoughtcrime.securesms.contacts.paged.ContactSearchData
import org.thoughtcrime.securesms.contacts.paged.ContactSearchKey
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel

class ContactSelectionListAdapter(
  context: Context,
  fixedContacts: Set<ContactSearchKey>,
  displayOptions: DisplayOptions,
  onClickCallbacks: OnContactSelectionClick,
  longClickCallbacks: LongClickCallbacks,
  storyContextMenuCallbacks: StoryContextMenuCallbacks,
  callButtonClickCallbacks: CallButtonClickCallbacks
) : ContactSearchAdapter(context, fixedContacts, displayOptions, onClickCallbacks, longClickCallbacks, storyContextMenuCallbacks, callButtonClickCallbacks) {

  init {
    ContactSelectionListModels.registerNewGroup(this, onClickCallbacks::onNewGroupClicked)
    ContactSelectionListModels.registerInviteToSignal(this, onClickCallbacks::onInviteToSignalClicked)
    ContactSelectionListModels.registerFindContacts(this, onClickCallbacks::onFindContactsClicked)
    ContactSelectionListModels.registerFindContactsBanner(this, onClickCallbacks::onDismissFindContactsBannerClicked, onClickCallbacks::onFindContactsClicked)
    ContactSelectionListModels.registerRefreshContacts(this, onClickCallbacks::onRefreshContactsClicked)
    ContactSelectionListModels.registerMoreHeader(this)
    ContactSelectionListModels.registerEmpty(this)
    ContactSelectionListModels.registerFindByUsername(this, onClickCallbacks::onFindByUsernameClicked)
    ContactSelectionListModels.registerFindByPhoneNumber(this, onClickCallbacks::onFindByPhoneNumberClicked)
  }

  class ArbitraryRepository : org.thoughtcrime.securesms.contacts.paged.ArbitraryRepository {

    override fun getSize(section: ContactSearchConfiguration.Section.Arbitrary, query: String?): Int {
      return section.types.size
    }

    override fun getData(section: ContactSearchConfiguration.Section.Arbitrary, query: String?, startIndex: Int, endIndex: Int, totalSearchSize: Int): List<ContactSearchData.Arbitrary> {
      check(section.types.size == 1)
      return listOf(ContactSearchData.Arbitrary(section.types.first()))
    }

    override fun getMappingModel(arbitrary: ContactSearchData.Arbitrary): MappingModel<*> {
      return when (ContactSelectionListModels.ArbitraryRow.fromCode(arbitrary.type)) {
        ContactSelectionListModels.ArbitraryRow.NEW_GROUP -> NewGroupModel()
        ContactSelectionListModels.ArbitraryRow.INVITE_TO_SIGNAL -> InviteToSignalModel()
        ContactSelectionListModels.ArbitraryRow.MORE_HEADING -> MoreHeaderModel()
        ContactSelectionListModels.ArbitraryRow.REFRESH_CONTACTS -> RefreshContactsModel()
        ContactSelectionListModels.ArbitraryRow.FIND_CONTACTS -> FindContactsModel()
        ContactSelectionListModels.ArbitraryRow.FIND_CONTACTS_BANNER -> FindContactsBannerModel()
        ContactSelectionListModels.ArbitraryRow.FIND_BY_PHONE_NUMBER -> FindByPhoneNumberModel()
        ContactSelectionListModels.ArbitraryRow.FIND_BY_USERNAME -> FindByUsernameModel()
      }
    }
  }

  interface OnContactSelectionClick : ClickCallbacks {
    fun onNewGroupClicked()
    fun onInviteToSignalClicked()
    fun onRefreshContactsClicked()
    fun onFindContactsClicked()
    fun onDismissFindContactsBannerClicked()
    fun onFindByPhoneNumberClicked()
    fun onFindByUsernameClicked()
  }
}
