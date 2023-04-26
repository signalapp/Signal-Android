package org.thoughtcrime.securesms

import android.content.Context
import android.view.View
import android.widget.TextView
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
  displayOptions: DisplayOptions,
  onClickCallbacks: OnContactSelectionClick,
  longClickCallbacks: LongClickCallbacks,
  storyContextMenuCallbacks: StoryContextMenuCallbacks,
  callButtonClickCallbacks: CallButtonClickCallbacks
) : ContactSearchAdapter(context, fixedContacts, displayOptions, onClickCallbacks, longClickCallbacks, storyContextMenuCallbacks, callButtonClickCallbacks) {

  init {
    registerFactory(NewGroupModel::class.java, LayoutFactory({ NewGroupViewHolder(it, onClickCallbacks::onNewGroupClicked) }, R.layout.contact_selection_new_group_item))
    registerFactory(InviteToSignalModel::class.java, LayoutFactory({ InviteToSignalViewHolder(it, onClickCallbacks::onInviteToSignalClicked) }, R.layout.contact_selection_invite_action_item))
    registerFactory(RefreshContactsModel::class.java, LayoutFactory({ RefreshContactsViewHolder(it, onClickCallbacks::onRefreshContactsClicked) }, R.layout.contact_selection_refresh_action_item))
    registerFactory(MoreHeaderModel::class.java, LayoutFactory({ MoreHeaderViewHolder(it) }, R.layout.contact_search_section_header))
    registerFactory(EmptyModel::class.java, LayoutFactory({ EmptyViewHolder(it) }, R.layout.contact_selection_empty_state))
  }

  class NewGroupModel : MappingModel<NewGroupModel> {
    override fun areItemsTheSame(newItem: NewGroupModel): Boolean = true
    override fun areContentsTheSame(newItem: NewGroupModel): Boolean = true
  }

  class InviteToSignalModel : MappingModel<InviteToSignalModel> {
    override fun areItemsTheSame(newItem: InviteToSignalModel): Boolean = true
    override fun areContentsTheSame(newItem: InviteToSignalModel): Boolean = true
  }

  class RefreshContactsModel : MappingModel<RefreshContactsModel> {
    override fun areItemsTheSame(newItem: RefreshContactsModel): Boolean = true
    override fun areContentsTheSame(newItem: RefreshContactsModel): Boolean = true
  }

  class MoreHeaderModel : MappingModel<MoreHeaderModel> {
    override fun areItemsTheSame(newItem: MoreHeaderModel): Boolean = true

    override fun areContentsTheSame(newItem: MoreHeaderModel): Boolean = true
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

  private class RefreshContactsViewHolder(itemView: View, onClickListener: () -> Unit) : MappingViewHolder<RefreshContactsModel>(itemView) {
    init {
      itemView.setOnClickListener { onClickListener() }
    }

    override fun bind(model: RefreshContactsModel) = Unit
  }

  private class MoreHeaderViewHolder(itemView: View) : MappingViewHolder<MoreHeaderModel>(itemView) {

    private val headerTextView: TextView = itemView.findViewById(R.id.section_header)

    override fun bind(model: MoreHeaderModel) {
      headerTextView.setText(R.string.contact_selection_activity__more)
    }
  }

  private class EmptyViewHolder(itemView: View) : MappingViewHolder<EmptyModel>(itemView) {

    private val emptyText: TextView = itemView.findViewById(R.id.search_no_results)

    override fun bind(model: EmptyModel) {
      emptyText.text = context.getString(R.string.SearchFragment_no_results, model.empty.query)
    }
  }

  class ArbitraryRepository : org.thoughtcrime.securesms.contacts.paged.ArbitraryRepository {

    enum class ArbitraryRow(val code: String) {
      NEW_GROUP("new-group"),
      INVITE_TO_SIGNAL("invite-to-signal"),
      MORE_HEADING("more-heading"),
      REFRESH_CONTACTS("refresh-contacts");

      companion object {
        fun fromCode(code: String) = values().first { it.code == code }
      }
    }

    override fun getSize(section: ContactSearchConfiguration.Section.Arbitrary, query: String?): Int {
      return section.types.size
    }

    override fun getData(section: ContactSearchConfiguration.Section.Arbitrary, query: String?, startIndex: Int, endIndex: Int, totalSearchSize: Int): List<ContactSearchData.Arbitrary> {
      check(section.types.size == 1)
      return listOf(ContactSearchData.Arbitrary(section.types.first()))
    }

    override fun getMappingModel(arbitrary: ContactSearchData.Arbitrary): MappingModel<*> {
      return when (ArbitraryRow.fromCode(arbitrary.type)) {
        ArbitraryRow.NEW_GROUP -> NewGroupModel()
        ArbitraryRow.INVITE_TO_SIGNAL -> InviteToSignalModel()
        ArbitraryRow.MORE_HEADING -> MoreHeaderModel()
        ArbitraryRow.REFRESH_CONTACTS -> RefreshContactsModel()
      }
    }
  }

  interface OnContactSelectionClick : ClickCallbacks {
    fun onNewGroupClicked()
    fun onInviteToSignalClicked()
    fun onRefreshContactsClicked()
  }
}
