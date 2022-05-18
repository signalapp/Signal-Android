package org.thoughtcrime.securesms.contacts.paged

import android.view.View
import android.widget.CheckBox
import android.widget.TextView
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.badges.BadgeImageView
import org.thoughtcrime.securesms.components.AvatarImageView
import org.thoughtcrime.securesms.components.FromTextView
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.adapter.mapping.LayoutFactory
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModelList
import org.thoughtcrime.securesms.util.adapter.mapping.MappingViewHolder
import org.thoughtcrime.securesms.util.visible

/**
 * Mapping Models and View Holders for ContactSearchData
 */
object ContactSearchItems {
  fun register(
    mappingAdapter: MappingAdapter,
    displayCheckBox: Boolean,
    recipientListener: (ContactSearchData.KnownRecipient, Boolean) -> Unit,
    storyListener: (ContactSearchData.Story, Boolean) -> Unit,
    expandListener: (ContactSearchData.Expand) -> Unit
  ) {
    mappingAdapter.registerFactory(
      StoryModel::class.java,
      LayoutFactory({ StoryViewHolder(it, displayCheckBox, storyListener) }, R.layout.contact_search_item)
    )
    mappingAdapter.registerFactory(
      RecipientModel::class.java,
      LayoutFactory({ KnownRecipientViewHolder(it, displayCheckBox, recipientListener) }, R.layout.contact_search_item)
    )
    mappingAdapter.registerFactory(
      HeaderModel::class.java,
      LayoutFactory({ HeaderViewHolder(it) }, R.layout.contact_search_section_header)
    )
    mappingAdapter.registerFactory(
      ExpandModel::class.java,
      LayoutFactory({ ExpandViewHolder(it, expandListener) }, R.layout.contacts_expand_item)
    )
  }

  fun toMappingModelList(contactSearchData: List<ContactSearchData?>, selection: Set<ContactSearchKey>): MappingModelList {
    return MappingModelList(
      contactSearchData.filterNotNull().map {
        when (it) {
          is ContactSearchData.Story -> StoryModel(it, selection.contains(it.contactSearchKey))
          is ContactSearchData.KnownRecipient -> RecipientModel(it, selection.contains(it.contactSearchKey))
          is ContactSearchData.Expand -> ExpandModel(it)
          is ContactSearchData.Header -> HeaderModel(it)
        }
      }
    )
  }

  /**
   * Story Model
   */
  private class StoryModel(val story: ContactSearchData.Story, val isSelected: Boolean) : MappingModel<StoryModel> {

    override fun areItemsTheSame(newItem: StoryModel): Boolean {
      return newItem.story == story
    }

    override fun areContentsTheSame(newItem: StoryModel): Boolean {
      return story.recipient.hasSameContent(newItem.story.recipient) && isSelected == newItem.isSelected
    }

    override fun getChangePayload(newItem: StoryModel): Any? {
      return if (story.recipient.hasSameContent(newItem.story.recipient) && newItem.isSelected != isSelected) {
        0
      } else {
        null
      }
    }
  }

  private class StoryViewHolder(itemView: View, displayCheckBox: Boolean, onClick: (ContactSearchData.Story, Boolean) -> Unit) : BaseRecipientViewHolder<StoryModel, ContactSearchData.Story>(itemView, displayCheckBox, onClick) {
    override fun isSelected(model: StoryModel): Boolean = model.isSelected
    override fun getData(model: StoryModel): ContactSearchData.Story = model.story
    override fun getRecipient(model: StoryModel): Recipient = model.story.recipient

    override fun bindNumberField(model: StoryModel) {
      number.visible = true

      val count = if (model.story.recipient.isGroup) {
        model.story.recipient.participants.size
      } else {
        model.story.viewerCount
      }

      val pluralId = when {
        model.story.recipient.isGroup -> R.plurals.ContactSearchItems__group_story_d_viewers
        model.story.recipient.isMyStory -> R.plurals.SelectViewersFragment__d_viewers
        else -> R.plurals.ContactSearchItems__private_story_d_viewers
      }

      number.text = context.resources.getQuantityString(pluralId, count, count)
    }
  }

  /**
   * Recipient model
   */
  private class RecipientModel(val knownRecipient: ContactSearchData.KnownRecipient, val isSelected: Boolean) : MappingModel<RecipientModel> {

    override fun areItemsTheSame(newItem: RecipientModel): Boolean {
      return newItem.knownRecipient == knownRecipient
    }

    override fun areContentsTheSame(newItem: RecipientModel): Boolean {
      return knownRecipient.recipient.hasSameContent(newItem.knownRecipient.recipient) && isSelected == newItem.isSelected
    }

    override fun getChangePayload(newItem: RecipientModel): Any? {
      return if (knownRecipient.recipient.hasSameContent(newItem.knownRecipient.recipient) && newItem.isSelected != isSelected) {
        0
      } else {
        null
      }
    }
  }

  private class KnownRecipientViewHolder(itemView: View, displayCheckBox: Boolean, onClick: (ContactSearchData.KnownRecipient, Boolean) -> Unit) : BaseRecipientViewHolder<RecipientModel, ContactSearchData.KnownRecipient>(itemView, displayCheckBox, onClick) {
    override fun isSelected(model: RecipientModel): Boolean = model.isSelected
    override fun getData(model: RecipientModel): ContactSearchData.KnownRecipient = model.knownRecipient
    override fun getRecipient(model: RecipientModel): Recipient = model.knownRecipient.recipient
  }

  /**
   * Base Recipient View Holder
   */
  private abstract class BaseRecipientViewHolder<T : MappingModel<T>, D : ContactSearchData>(itemView: View, private val displayCheckBox: Boolean, val onClick: (D, Boolean) -> Unit) : MappingViewHolder<T>(itemView) {

    protected val avatar: AvatarImageView = itemView.findViewById(R.id.contact_photo_image)
    protected val badge: BadgeImageView = itemView.findViewById(R.id.contact_badge)
    protected val checkbox: CheckBox = itemView.findViewById(R.id.check_box)
    protected val name: FromTextView = itemView.findViewById(R.id.name)
    protected val number: TextView = itemView.findViewById(R.id.number)
    protected val label: TextView = itemView.findViewById(R.id.label)
    protected val smsTag: View = itemView.findViewById(R.id.sms_tag)

    override fun bind(model: T) {
      checkbox.visible = displayCheckBox
      checkbox.isChecked = isSelected(model)
      itemView.setOnClickListener { onClick(getData(model), isSelected(model)) }

      if (payload.isNotEmpty()) {
        return
      }

      name.setText(getRecipient(model))
      avatar.setAvatar(getRecipient(model))
      badge.setBadgeFromRecipient(getRecipient(model))

      bindNumberField(model)
      bindLabelField(model)
      bindSmsTagField(model)
    }

    protected open fun bindNumberField(model: T) {
      number.visible = getRecipient(model).isGroup
      if (getRecipient(model).isGroup) {
        number.text = getRecipient(model).participants
          .take(10)
          .sortedWith(IsSelfComparator()).joinToString(", ") {
            if (it.isSelf) {
              context.getString(R.string.ConversationTitleView_you)
            } else {
              it.getShortDisplayName(context)
            }
          }
      }
    }

    protected open fun bindLabelField(model: T) {
      label.visible = false
    }

    protected open fun bindSmsTagField(model: T) {
      smsTag.visible = isSmsContact(model)
    }

    private fun isSmsContact(model: T): Boolean {
      return (getRecipient(model).isForceSmsSelection || getRecipient(model).isUnregistered) && !getRecipient(model).isDistributionList
    }

    abstract fun isSelected(model: T): Boolean
    abstract fun getData(model: T): D
    abstract fun getRecipient(model: T): Recipient
  }

  /**
   * Mapping Model for section headers
   */
  private class HeaderModel(val header: ContactSearchData.Header) : MappingModel<HeaderModel> {
    override fun areItemsTheSame(newItem: HeaderModel): Boolean {
      return header.sectionKey == newItem.header.sectionKey
    }

    override fun areContentsTheSame(newItem: HeaderModel): Boolean {
      return areItemsTheSame(newItem) &&
        header.action?.icon == newItem.header.action?.icon &&
        header.action?.label == newItem.header.action?.label
    }
  }

  /**
   * View Holder for section headers
   */
  private class HeaderViewHolder(itemView: View) : MappingViewHolder<HeaderModel>(itemView) {

    private val headerTextView: TextView = itemView.findViewById(R.id.section_header)
    private val headerActionView: TextView = itemView.findViewById(R.id.section_header_action)

    override fun bind(model: HeaderModel) {
      headerTextView.setText(
        when (model.header.sectionKey) {
          ContactSearchConfiguration.SectionKey.STORIES -> R.string.ContactsCursorLoader_my_stories
          ContactSearchConfiguration.SectionKey.RECENTS -> R.string.ContactsCursorLoader_recent_chats
          ContactSearchConfiguration.SectionKey.INDIVIDUALS -> R.string.ContactsCursorLoader_contacts
          ContactSearchConfiguration.SectionKey.GROUPS -> R.string.ContactsCursorLoader_groups
        }
      )

      if (model.header.action != null) {
        headerActionView.visible = true
        headerActionView.setCompoundDrawablesRelativeWithIntrinsicBounds(model.header.action.icon, 0, 0, 0)
        headerActionView.setText(model.header.action.label)
        headerActionView.setOnClickListener { model.header.action.action.run() }
      } else {
        headerActionView.visible = false
      }
    }
  }

  /**
   * Mapping Model for expandable content rows.
   */
  private class ExpandModel(val expand: ContactSearchData.Expand) : MappingModel<ExpandModel> {
    override fun areItemsTheSame(newItem: ExpandModel): Boolean {
      return expand.contactSearchKey == newItem.expand.contactSearchKey
    }

    override fun areContentsTheSame(newItem: ExpandModel): Boolean {
      return areItemsTheSame(newItem)
    }
  }

  /**
   * View Holder for expandable content rows.
   */
  private class ExpandViewHolder(itemView: View, private val expandListener: (ContactSearchData.Expand) -> Unit) : MappingViewHolder<ExpandModel>(itemView) {
    override fun bind(model: ExpandModel) {
      itemView.setOnClickListener { expandListener.invoke(model.expand) }
    }
  }

  private class IsSelfComparator : Comparator<Recipient> {
    override fun compare(lhs: Recipient?, rhs: Recipient?): Int {
      val isLeftSelf = lhs?.isSelf == true
      val isRightSelf = rhs?.isSelf == true

      return if (isLeftSelf == isRightSelf) 0 else if (isLeftSelf) 1 else -1
    }
  }
}
