/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms

import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import org.thoughtcrime.securesms.contacts.paged.ContactSearchModels.EmptyModel
import org.thoughtcrime.securesms.util.adapter.mapping.LayoutFactory
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel
import org.thoughtcrime.securesms.util.adapter.mapping.MappingViewHolder
import org.thoughtcrime.securesms.util.adapter.mapping.compose.MappingEntryProvider
import org.thoughtcrime.securesms.util.adapter.mapping.compose.MappingEntryProviderBuilder

/**
 * Holds the [MappingModel]s and [MappingViewHolder]s used by [ContactSelectionListAdapter] on top of
 * the base set in [org.thoughtcrime.securesms.contacts.paged.ContactSearchModels], along with helpers
 * for registering them on a [MappingAdapter] (RecyclerView) or building a [MappingEntryProvider]
 * (Compose).
 */
object ContactSelectionListModels {

  fun registerNewGroup(mappingAdapter: MappingAdapter, onClick: () -> Unit) {
    mappingAdapter.registerFactory(
      NewGroupModel::class.java,
      LayoutFactory({ NewGroupViewHolder(it, onClick) }, R.layout.contact_selection_new_group_item)
    )
  }

  fun registerInviteToSignal(mappingAdapter: MappingAdapter, onClick: () -> Unit) {
    mappingAdapter.registerFactory(
      InviteToSignalModel::class.java,
      LayoutFactory({ InviteToSignalViewHolder(it, onClick) }, R.layout.contact_selection_invite_action_item)
    )
  }

  fun registerFindContacts(mappingAdapter: MappingAdapter, onClick: () -> Unit) {
    mappingAdapter.registerFactory(
      FindContactsModel::class.java,
      LayoutFactory({ FindContactsViewHolder(it, onClick) }, R.layout.contact_selection_find_contacts_item)
    )
  }

  fun registerFindContactsBanner(mappingAdapter: MappingAdapter, onDismiss: () -> Unit, onClick: () -> Unit) {
    mappingAdapter.registerFactory(
      FindContactsBannerModel::class.java,
      LayoutFactory({ FindContactsBannerViewHolder(it, onDismiss, onClick) }, R.layout.contact_selection_find_contacts_banner_item)
    )
  }

  fun registerRefreshContacts(mappingAdapter: MappingAdapter, onClick: () -> Unit) {
    mappingAdapter.registerFactory(
      RefreshContactsModel::class.java,
      LayoutFactory({ RefreshContactsViewHolder(it, onClick) }, R.layout.contact_selection_refresh_action_item)
    )
  }

  fun registerMoreHeader(mappingAdapter: MappingAdapter) {
    mappingAdapter.registerFactory(
      MoreHeaderModel::class.java,
      LayoutFactory({ MoreHeaderViewHolder(it) }, R.layout.contact_search_section_header)
    )
  }

  fun registerEmpty(mappingAdapter: MappingAdapter) {
    mappingAdapter.registerFactory(
      EmptyModel::class.java,
      LayoutFactory({ EmptyViewHolder(it) }, R.layout.contact_selection_empty_state)
    )
  }

  fun registerFindByUsername(mappingAdapter: MappingAdapter, onClick: () -> Unit) {
    mappingAdapter.registerFactory(
      FindByUsernameModel::class.java,
      LayoutFactory({ FindByUsernameViewHolder(it, onClick) }, R.layout.contact_selection_find_by_username_item)
    )
  }

  fun registerFindByPhoneNumber(mappingAdapter: MappingAdapter, onClick: () -> Unit) {
    mappingAdapter.registerFactory(
      FindByPhoneNumberModel::class.java,
      LayoutFactory({ FindByPhoneNumberViewHolder(it, onClick) }, R.layout.contact_selection_find_by_phone_number_item)
    )
  }

  /**
   * Returns a [MappingEntryProvider] containing the same set of view holders registered by the
   * adapter-side `register*` methods, suitable for use with a Compose `MappingLazyColumn`.
   */
  @JvmStatic
  fun composeEntries(
    callback: Callback
  ): MappingEntryProvider<Any> {
    return MappingEntryProviderBuilder<Any>().apply {
      viewHolder<NewGroupModel> { context ->
        LayoutFactory(
          { view -> NewGroupViewHolder(view, callback::onNewGroupClicked) },
          R.layout.contact_selection_new_group_item
        ).createViewHolder(FrameLayout(context))
      }
      viewHolder<InviteToSignalModel> { context ->
        LayoutFactory(
          { view -> InviteToSignalViewHolder(view, callback::onInviteToSignalClicked) },
          R.layout.contact_selection_invite_action_item
        ).createViewHolder(FrameLayout(context))
      }
      viewHolder<FindContactsModel> { context ->
        LayoutFactory(
          { view -> FindContactsViewHolder(view, callback::onFindContactsClicked) },
          R.layout.contact_selection_find_contacts_item
        ).createViewHolder(FrameLayout(context))
      }
      viewHolder<FindContactsBannerModel> { context ->
        LayoutFactory(
          { view -> FindContactsBannerViewHolder(view, callback::onDismissFindContactsBannerClicked, callback::onFindContactsClicked) },
          R.layout.contact_selection_find_contacts_banner_item
        ).createViewHolder(FrameLayout(context))
      }
      viewHolder<RefreshContactsModel> { context ->
        LayoutFactory(
          { view -> RefreshContactsViewHolder(view, callback::onRefreshContactsClicked) },
          R.layout.contact_selection_refresh_action_item
        ).createViewHolder(FrameLayout(context))
      }
      viewHolder<MoreHeaderModel> { context ->
        LayoutFactory(
          { view -> MoreHeaderViewHolder(view) },
          R.layout.contact_search_section_header
        ).createViewHolder(FrameLayout(context))
      }
      viewHolder<EmptyModel> { context ->
        LayoutFactory(
          { view -> EmptyViewHolder(view) },
          R.layout.contact_selection_empty_state
        ).createViewHolder(FrameLayout(context))
      }
      viewHolder<FindByUsernameModel> { context ->
        LayoutFactory(
          { view -> FindByUsernameViewHolder(view, callback::onFindByUsernameClicked) },
          R.layout.contact_selection_find_by_username_item
        ).createViewHolder(FrameLayout(context))
      }
      viewHolder<FindByPhoneNumberModel> { context ->
        LayoutFactory(
          { view -> FindByPhoneNumberViewHolder(view, callback::onFindByPhoneNumberClicked) },
          R.layout.contact_selection_find_by_phone_number_item
        ).createViewHolder(FrameLayout(context))
      }
    }.build()
  }

  interface Callback {
    fun onNewGroupClicked()
    fun onInviteToSignalClicked()
    fun onFindContactsClicked()
    fun onDismissFindContactsBannerClicked()
    fun onRefreshContactsClicked()
    fun onFindByUsernameClicked()
    fun onFindByPhoneNumberClicked()
  }

  enum class ArbitraryRow(val code: String) {
    NEW_GROUP("new-group"),
    INVITE_TO_SIGNAL("invite-to-signal"),
    MORE_HEADING("more-heading"),
    REFRESH_CONTACTS("refresh-contacts"),
    FIND_CONTACTS("find-contacts"),
    FIND_CONTACTS_BANNER("find-contacts-banner"),
    FIND_BY_USERNAME("find-by-username"),
    FIND_BY_PHONE_NUMBER("find-by-phone-number");

    companion object {
      fun fromCode(code: String) = entries.first { it.code == code }
    }
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

  class FindContactsModel : MappingModel<FindContactsModel> {
    override fun areItemsTheSame(newItem: FindContactsModel): Boolean = true
    override fun areContentsTheSame(newItem: FindContactsModel): Boolean = true
  }

  class FindContactsBannerModel : MappingModel<FindContactsBannerModel> {
    override fun areItemsTheSame(newItem: FindContactsBannerModel): Boolean = true
    override fun areContentsTheSame(newItem: FindContactsBannerModel): Boolean = true
  }

  class FindByUsernameModel : MappingModel<FindByUsernameModel> {
    override fun areItemsTheSame(newItem: FindByUsernameModel): Boolean = true
    override fun areContentsTheSame(newItem: FindByUsernameModel): Boolean = true
  }

  class FindByPhoneNumberModel : MappingModel<FindByPhoneNumberModel> {
    override fun areItemsTheSame(newItem: FindByPhoneNumberModel): Boolean = true
    override fun areContentsTheSame(newItem: FindByPhoneNumberModel): Boolean = true
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

  private class FindContactsViewHolder(itemView: View, onClickListener: () -> Unit) : MappingViewHolder<FindContactsModel>(itemView) {
    init {
      itemView.setOnClickListener { onClickListener() }
    }

    override fun bind(model: FindContactsModel) = Unit
  }

  private class FindContactsBannerViewHolder(itemView: View, onDismissListener: () -> Unit, onClickListener: () -> Unit) : MappingViewHolder<FindContactsBannerModel>(itemView) {
    init {
      itemView.findViewById<MaterialButton>(R.id.no_thanks_button).setOnClickListener { onDismissListener() }
      itemView.findViewById<MaterialButton>(R.id.allow_contacts_button).setOnClickListener { onClickListener() }
    }

    override fun bind(model: FindContactsBannerModel) = Unit
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
      emptyText.text = context.getString(R.string.SearchFragment_no_results, model.empty.query ?: "")
    }
  }

  private class FindByPhoneNumberViewHolder(itemView: View, onClickListener: () -> Unit) : MappingViewHolder<FindByPhoneNumberModel>(itemView) {

    init {
      itemView.setOnClickListener { onClickListener() }
    }

    override fun bind(model: FindByPhoneNumberModel) = Unit
  }

  private class FindByUsernameViewHolder(itemView: View, onClickListener: () -> Unit) : MappingViewHolder<FindByUsernameModel>(itemView) {

    init {
      itemView.setOnClickListener { onClickListener() }
    }

    override fun bind(model: FindByUsernameModel) = Unit
  }
}
