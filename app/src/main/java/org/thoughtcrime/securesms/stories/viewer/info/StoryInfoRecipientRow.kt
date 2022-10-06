package org.thoughtcrime.securesms.stories.viewer.info

import android.view.View
import android.widget.TextView
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.AvatarImageView
import org.thoughtcrime.securesms.messagedetails.RecipientDeliveryStatus
import org.thoughtcrime.securesms.util.DateUtils
import org.thoughtcrime.securesms.util.adapter.mapping.LayoutFactory
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel
import org.thoughtcrime.securesms.util.adapter.mapping.MappingViewHolder
import java.util.Locale

/**
 * Holds information needed to render a single recipient row in the info sheet.
 */
object StoryInfoRecipientRow {
  fun register(mappingAdapter: MappingAdapter) {
    mappingAdapter.registerFactory(Model::class.java, LayoutFactory(::ViewHolder, R.layout.story_info_recipient_row))
  }

  class Model(
    val recipientDeliveryStatus: RecipientDeliveryStatus
  ) : MappingModel<Model> {
    override fun areItemsTheSame(newItem: Model): Boolean {
      return recipientDeliveryStatus.recipient.id == newItem.recipientDeliveryStatus.recipient.id
    }

    override fun areContentsTheSame(newItem: Model): Boolean {
      return recipientDeliveryStatus.recipient.hasSameContent(newItem.recipientDeliveryStatus.recipient) &&
        recipientDeliveryStatus.timestamp == newItem.recipientDeliveryStatus.timestamp
    }
  }

  private class ViewHolder(itemView: View) : MappingViewHolder<Model>(itemView) {

    private val avatarView: AvatarImageView = itemView.findViewById(R.id.story_info_avatar)
    private val nameView: TextView = itemView.findViewById(R.id.story_info_display_name)
    private val timestampView: TextView = itemView.findViewById(R.id.story_info_timestamp)

    override fun bind(model: Model) {
      avatarView.setRecipient(model.recipientDeliveryStatus.recipient)
      nameView.text = model.recipientDeliveryStatus.recipient.getDisplayName(context)
      timestampView.text = DateUtils.getTimeString(context, Locale.getDefault(), model.recipientDeliveryStatus.timestamp)
    }
  }
}
