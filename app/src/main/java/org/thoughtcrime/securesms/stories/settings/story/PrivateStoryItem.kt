package org.thoughtcrime.securesms.stories.settings.story

import android.view.View
import android.widget.TextView
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.AvatarImageView
import org.thoughtcrime.securesms.components.settings.PreferenceModel
import org.thoughtcrime.securesms.database.model.DistributionListPartialRecord
import org.thoughtcrime.securesms.database.model.DistributionListRecord
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.adapter.mapping.LayoutFactory
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.adapter.mapping.MappingViewHolder

object PrivateStoryItem {

  fun register(mappingAdapter: MappingAdapter) {
    mappingAdapter.registerFactory(NewModel::class.java, LayoutFactory(::NewViewHolder, R.layout.stories_private_story_new_item))
    mappingAdapter.registerFactory(AddViewerModel::class.java, LayoutFactory(::AddViewerViewHolder, R.layout.stories_private_story_add_viewer_item))
    mappingAdapter.registerFactory(RecipientModel::class.java, LayoutFactory(::RecipientViewHolder, R.layout.stories_private_story_recipient_item))
    mappingAdapter.registerFactory(Model::class.java, LayoutFactory(::ViewHolder, R.layout.stories_private_story_item))
    mappingAdapter.registerFactory(PartialModel::class.java, LayoutFactory(::PartialViewHolder, R.layout.stories_private_story_item))
  }

  class NewModel(
    val onClick: () -> Unit
  ) : PreferenceModel<NewModel>() {
    override fun areItemsTheSame(newItem: NewModel): Boolean = true
  }

  class AddViewerModel(
    val onClick: () -> Unit
  ) : PreferenceModel<AddViewerModel>() {
    override fun areItemsTheSame(newItem: AddViewerModel): Boolean = true
  }

  class RecipientModel(
    val recipient: Recipient,
    val onClick: (RecipientModel) -> Unit
  ) : PreferenceModel<RecipientModel>() {
    override fun areItemsTheSame(newItem: RecipientModel): Boolean = newItem.recipient == recipient

    override fun areContentsTheSame(newItem: RecipientModel): Boolean {
      return newItem.recipient.hasSameContent(recipient) && super.areContentsTheSame(newItem)
    }
  }

  class Model(
    val privateStoryItemData: DistributionListRecord,
    val onClick: (Model) -> Unit
  ) : PreferenceModel<Model>() {
    override fun areItemsTheSame(newItem: Model): Boolean {
      return newItem.privateStoryItemData.id == privateStoryItemData.id
    }

    override fun areContentsTheSame(newItem: Model): Boolean {
      return newItem.privateStoryItemData == privateStoryItemData &&
        super.areContentsTheSame(newItem)
    }
  }

  class PartialModel(
    val privateStoryItemData: DistributionListPartialRecord,
    val onClick: (PartialModel) -> Unit
  ) : PreferenceModel<PartialModel>() {
    override fun areItemsTheSame(newItem: PartialModel): Boolean {
      return newItem.privateStoryItemData.id == privateStoryItemData.id
    }

    override fun areContentsTheSame(newItem: PartialModel): Boolean {
      return newItem.privateStoryItemData == privateStoryItemData &&
        super.areContentsTheSame(newItem)
    }
  }

  private class RecipientViewHolder(itemView: View) : MappingViewHolder<RecipientModel>(itemView) {

    private val name: TextView = itemView.findViewById(R.id.label)
    private val avatar: AvatarImageView = itemView.findViewById(R.id.avatar)

    override fun bind(model: RecipientModel) {
      itemView.setOnClickListener { model.onClick(model) }
      avatar.setRecipient(model.recipient)

      if (model.recipient.isSelf) {
        name.setText(R.string.MyStorySettingsFragment__my_story)
      } else {
        name.text = model.recipient.getDisplayName(context)
      }
    }
  }

  private class NewViewHolder(itemView: View) : MappingViewHolder<NewModel>(itemView) {
    override fun bind(model: NewModel) {
      itemView.setOnClickListener { model.onClick() }
    }
  }

  private class AddViewerViewHolder(itemView: View) : MappingViewHolder<AddViewerModel>(itemView) {
    override fun bind(model: AddViewerModel) {
      itemView.setOnClickListener { model.onClick() }
    }
  }

  private class ViewHolder(itemView: View) : MappingViewHolder<Model>(itemView) {

    private val label: TextView = itemView.findViewById(R.id.label)

    override fun bind(model: Model) {
      itemView.setOnClickListener { model.onClick(model) }
      label.text = model.privateStoryItemData.name
    }
  }

  private class PartialViewHolder(itemView: View) : MappingViewHolder<PartialModel>(itemView) {

    private val label: TextView = itemView.findViewById(R.id.label)

    override fun bind(model: PartialModel) {
      itemView.setOnClickListener { model.onClick(model) }
      label.text = model.privateStoryItemData.name
    }
  }
}
