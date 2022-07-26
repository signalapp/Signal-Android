package org.thoughtcrime.securesms.contacts.paged

import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.badges.BadgeImageView
import org.thoughtcrime.securesms.components.AvatarImageView
import org.thoughtcrime.securesms.components.FromTextView
import org.thoughtcrime.securesms.components.menu.ActionItem
import org.thoughtcrime.securesms.components.menu.SignalContextMenu
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.adapter.mapping.LayoutFactory
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModelList
import org.thoughtcrime.securesms.util.adapter.mapping.MappingViewHolder
import org.thoughtcrime.securesms.util.visible

private typealias StoryClickListener = (View, ContactSearchData.Story, Boolean) -> Unit
private typealias RecipientClickListener = (View, ContactSearchData.KnownRecipient, Boolean) -> Unit

/**
 * Mapping Models and View Holders for ContactSearchData
 */
object ContactSearchItems {
  fun register(
    mappingAdapter: MappingAdapter,
    displayCheckBox: Boolean,
    recipientListener: RecipientClickListener,
    storyListener: StoryClickListener,
    storyContextMenuCallbacks: StoryContextMenuCallbacks,
    expandListener: (ContactSearchData.Expand) -> Unit
  ) {
    mappingAdapter.registerFactory(
      StoryModel::class.java,
      LayoutFactory({ StoryViewHolder(it, displayCheckBox, storyListener, storyContextMenuCallbacks) }, R.layout.contact_search_item)
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
          is ContactSearchData.Story -> StoryModel(it, selection.contains(it.contactSearchKey), SignalStore.storyValues().userHasBeenNotifiedAboutStories)
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
  private class StoryModel(val story: ContactSearchData.Story, val isSelected: Boolean, val hasBeenNotified: Boolean) : MappingModel<StoryModel> {

    override fun areItemsTheSame(newItem: StoryModel): Boolean {
      return newItem.story == story
    }

    override fun areContentsTheSame(newItem: StoryModel): Boolean {
      return story.recipient.hasSameContent(newItem.story.recipient) &&
        isSelected == newItem.isSelected &&
        hasBeenNotified == newItem.hasBeenNotified
    }

    override fun getChangePayload(newItem: StoryModel): Any? {
      return if (story.recipient.hasSameContent(newItem.story.recipient) &&
        hasBeenNotified == newItem.hasBeenNotified &&
        newItem.isSelected != isSelected
      ) {
        0
      } else {
        null
      }
    }
  }

  private class StoryViewHolder(itemView: View, displayCheckBox: Boolean, onClick: StoryClickListener, private val storyContextMenuCallbacks: StoryContextMenuCallbacks) : BaseRecipientViewHolder<StoryModel, ContactSearchData.Story>(itemView, displayCheckBox, onClick) {
    override fun isSelected(model: StoryModel): Boolean = model.isSelected
    override fun getData(model: StoryModel): ContactSearchData.Story = model.story
    override fun getRecipient(model: StoryModel): Recipient = model.story.recipient

    override fun bindNumberField(model: StoryModel) {
      number.visible = true

      val count = if (model.story.recipient.isGroup) {
        model.story.recipient.participantIds.size
      } else {
        model.story.viewerCount
      }

      if (model.story.recipient.isMyStory && !model.hasBeenNotified) {
        number.setText(R.string.ContactSearchItems__tap_to_choose_your_viewers)
      } else {
        val pluralId = when {
          model.story.recipient.isGroup -> R.plurals.ContactSearchItems__group_story_d_viewers
          model.story.recipient.isMyStory -> R.plurals.SelectViewersFragment__d_viewers
          else -> R.plurals.ContactSearchItems__private_story_d_viewers
        }

        number.text = context.resources.getQuantityString(pluralId, count, count)
      }
    }

    override fun bindAvatar(model: StoryModel) {
      if (model.story.recipient.isMyStory) {
        avatar.setAvatarUsingProfile(Recipient.self())
      } else {
        super.bindAvatar(model)
      }
    }

    override fun bindLongPress(model: StoryModel) {
      itemView.setOnLongClickListener {
        val actions: List<ActionItem> = when {
          model.story.recipient.isMyStory -> getMyStoryContextMenuActions(model)
          model.story.recipient.isGroup -> getGroupStoryContextMenuActions(model)
          model.story.recipient.isDistributionList -> getPrivateStoryContextMenuActions(model)
          else -> error("Unsupported story target. Not a group or distribution list.")
        }

        SignalContextMenu.Builder(itemView, itemView.rootView as ViewGroup)
          .offsetX(context.resources.getDimensionPixelSize(R.dimen.dsl_settings_gutter))
          .show(actions)

        true
      }
    }

    private fun getMyStoryContextMenuActions(model: StoryModel): List<ActionItem> {
      return listOf(
        ActionItem(R.drawable.ic_settings_24, context.getString(R.string.ContactSearchItems__story_settings)) {
          storyContextMenuCallbacks.onOpenStorySettings(model.story)
        }
      )
    }

    private fun getGroupStoryContextMenuActions(model: StoryModel): List<ActionItem> {
      return listOf(
        ActionItem(R.drawable.ic_minus_circle_20, context.getString(R.string.ContactSearchItems__remove_story)) {
          storyContextMenuCallbacks.onRemoveGroupStory(model.story, model.isSelected)
        }
      )
    }

    private fun getPrivateStoryContextMenuActions(model: StoryModel): List<ActionItem> {
      return listOf(
        ActionItem(R.drawable.ic_settings_24, context.getString(R.string.ContactSearchItems__story_settings)) {
          storyContextMenuCallbacks.onOpenStorySettings(model.story)
        },
        ActionItem(R.drawable.ic_delete_24, context.getString(R.string.ContactSearchItems__delete_story), R.color.signal_colorError) {
          storyContextMenuCallbacks.onDeletePrivateStory(model.story, model.isSelected)
        }
      )
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

  private class KnownRecipientViewHolder(itemView: View, displayCheckBox: Boolean, onClick: RecipientClickListener) : BaseRecipientViewHolder<RecipientModel, ContactSearchData.KnownRecipient>(itemView, displayCheckBox, onClick) {
    override fun isSelected(model: RecipientModel): Boolean = model.isSelected
    override fun getData(model: RecipientModel): ContactSearchData.KnownRecipient = model.knownRecipient
    override fun getRecipient(model: RecipientModel): Recipient = model.knownRecipient.recipient
  }

  /**
   * Base Recipient View Holder
   */
  private abstract class BaseRecipientViewHolder<T : MappingModel<T>, D : ContactSearchData>(itemView: View, private val displayCheckBox: Boolean, val onClick: (View, D, Boolean) -> Unit) : MappingViewHolder<T>(itemView) {

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
      itemView.setOnClickListener { onClick(itemView, getData(model), isSelected(model)) }
      bindLongPress(model)

      if (payload.isNotEmpty()) {
        return
      }

      name.setText(getRecipient(model))
      badge.setBadgeFromRecipient(getRecipient(model))

      bindAvatar(model)
      bindNumberField(model)
      bindLabelField(model)
      bindSmsTagField(model)
    }

    protected open fun bindAvatar(model: T) {
      avatar.setAvatar(getRecipient(model))
    }

    protected open fun bindNumberField(model: T) {
      number.visible = getRecipient(model).isGroup
      if (getRecipient(model).isGroup) {
        number.text = getRecipient(model).participantIds
          .take(10)
          .map { id -> Recipient.resolved(id) }
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

    protected open fun bindLongPress(model: T) = Unit

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

  interface StoryContextMenuCallbacks {
    fun onOpenStorySettings(story: ContactSearchData.Story)
    fun onRemoveGroupStory(story: ContactSearchData.Story, isSelected: Boolean)
    fun onDeletePrivateStory(story: ContactSearchData.Story, isSelected: Boolean)
  }
}
