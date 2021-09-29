package org.thoughtcrime.securesms.badges.models

import android.graphics.drawable.Drawable
import android.view.View
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.bumptech.glide.request.target.CustomViewTarget
import com.bumptech.glide.request.transition.Transition
import org.signal.core.util.DimensionUnit
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.badges.Badges.insetWithOutline
import org.thoughtcrime.securesms.components.AvatarImageView
import org.thoughtcrime.securesms.components.settings.PreferenceModel
import org.thoughtcrime.securesms.mms.GlideApp
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.MappingAdapter
import org.thoughtcrime.securesms.util.MappingViewHolder

object FeaturedBadgePreview {

  fun register(mappingAdapter: MappingAdapter) {
    mappingAdapter.registerFactory(Model::class.java, MappingAdapter.LayoutFactory({ ViewHolder(it) }, R.layout.featured_badge_preview_preference))
  }

  data class Model(val badge: Badge?) : PreferenceModel<Model>() {
    override fun areItemsTheSame(newItem: Model): Boolean {
      return newItem.badge?.id == badge?.id
    }

    override fun areContentsTheSame(newItem: Model): Boolean {
      return super.areContentsTheSame(newItem) && badge == newItem.badge
    }
  }

  class ViewHolder(itemView: View) : MappingViewHolder<Model>(itemView) {

    private val avatar: AvatarImageView = itemView.findViewById(R.id.avatar)
    private val badge: ImageView = itemView.findViewById(R.id.badge)
    private val target: Target = Target(badge)

    override fun bind(model: Model) {
      avatar.setRecipient(Recipient.self())
      avatar.disableQuickContact()

      if (model.badge != null) {
        GlideApp.with(badge)
          .load(model.badge)
          .into(target)
      } else {
        GlideApp.with(badge).clear(badge)
        badge.setImageDrawable(null)
      }
    }
  }

  private class Target(view: ImageView) : CustomViewTarget<ImageView, Drawable>(view) {
    override fun onLoadFailed(errorDrawable: Drawable?) {
      view.setImageDrawable(errorDrawable)
    }

    override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
      view.setImageDrawable(
        resource.insetWithOutline(
          DimensionUnit.DP.toPixels(2.5f),
          ContextCompat.getColor(view.context, R.color.signal_background_primary)
        )
      )
    }

    override fun onResourceCleared(placeholder: Drawable?) {
      view.setImageDrawable(placeholder)
    }
  }
}
