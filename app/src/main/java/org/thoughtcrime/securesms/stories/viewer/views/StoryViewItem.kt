package org.thoughtcrime.securesms.stories.viewer.views

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.signal.core.util.DimensionUnit
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.AvatarImageView
import org.thoughtcrime.securesms.components.menu.ActionItem
import org.thoughtcrime.securesms.components.menu.SignalContextMenu
import org.thoughtcrime.securesms.components.settings.PreferenceModel
import org.thoughtcrime.securesms.util.DateUtils
import org.thoughtcrime.securesms.util.adapter.mapping.LayoutFactory
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.adapter.mapping.MappingViewHolder
import java.util.Locale

/**
 * UI consisting of a recipient's avatar, name, and when they viewed a story
 */
object StoryViewItem {

  fun register(mappingAdapter: MappingAdapter) {
    mappingAdapter.registerFactory(Model::class.java, LayoutFactory(::ViewHolder, R.layout.stories_story_view_item))
  }

  class Model(
    val storyViewItemData: StoryViewItemData,
    val canRemoveMember: Boolean,
    val goToChat: (Model) -> Unit,
    val removeFromStory: (Model) -> Unit
  ) : PreferenceModel<Model>() {
    override fun areItemsTheSame(newItem: Model): Boolean {
      return storyViewItemData.recipient == newItem.storyViewItemData.recipient
    }

    override fun areContentsTheSame(newItem: Model): Boolean {
      return storyViewItemData == newItem.storyViewItemData &&
        storyViewItemData.recipient.hasSameContent(newItem.storyViewItemData.recipient) &&
        canRemoveMember == newItem.canRemoveMember &&
        super.areContentsTheSame(newItem)
    }
  }

  private class ViewHolder(itemView: View) : MappingViewHolder<Model>(itemView) {

    private val avatarView: AvatarImageView = itemView.findViewById(R.id.avatar)
    private val nameView: TextView = itemView.findViewById(R.id.name)
    private val viewedAtView: TextView = itemView.findViewById(R.id.viewed_at)

    override fun bind(model: Model) {
      avatarView.setAvatar(model.storyViewItemData.recipient)
      nameView.text = model.storyViewItemData.recipient.getDisplayName(context)
      viewedAtView.text = formatDate(model.storyViewItemData.timeViewedInMillis)

      itemView.setOnClickListener {
        showContextMenu(model)
      }
    }

    private fun formatDate(dateInMilliseconds: Long): String {
      return DateUtils.getBriefRelativeTimeSpanString(context, Locale.getDefault(), dateInMilliseconds)
    }

    private fun showContextMenu(model: Model) {
      itemView.isSelected = true

      val actions = mutableListOf<ActionItem>()

      actions.add(
        ActionItem(
          iconRes = R.drawable.ic_open_24_tinted,
          title = context.getString(R.string.StoriesLandingItem__go_to_chat),
          action = {
            model.goToChat(model)
          }
        )
      )

      if (model.canRemoveMember) {
        actions.add(
          ActionItem(
            iconRes = R.drawable.ic_minus_circle_20,
            title = context.getString(R.string.StoryViewItem__remove_viewer),
            action = {
              model.removeFromStory(model)
            }
          )
        )
      }

      SignalContextMenu.Builder(itemView, itemView.rootView as ViewGroup)
        .offsetY(DimensionUnit.DP.toPixels(16f).toInt())
        .onDismiss { itemView.isSelected = false }
        .show(actions)
    }
  }
}
