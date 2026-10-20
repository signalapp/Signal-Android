/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.badges.models

import android.animation.ObjectAnimator
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import org.signal.core.models.badges.Badge
import org.signal.core.ui.util.ThemeUtil
import org.signal.glide.badges.BadgeSpriteTransformation
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.PreferenceModel
import org.thoughtcrime.securesms.util.adapter.mapping.LayoutFactory
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.adapter.mapping.MappingViewHolder

typealias OnBadgeClicked = (Badge, Boolean, Boolean) -> Unit

/**
 * Selectable badge grid item used when displaying a user's badges in a list.
 */
object SelectableBadge {

  private val SELECTION_CHANGED = Any()

  class EmptyModel : PreferenceModel<EmptyModel>() {
    override fun areItemsTheSame(newItem: EmptyModel): Boolean = true
  }

  class EmptyViewHolder(itemView: View) : MappingViewHolder<EmptyModel>(itemView) {

    private val name: TextView = itemView.findViewById(R.id.name)

    init {
      itemView.isEnabled = false
      itemView.isFocusable = false
      itemView.isClickable = false
      itemView.visibility = View.INVISIBLE

      name.text = " "
    }

    override fun bind(model: EmptyModel) = Unit
  }

  class Model(
    val badge: Badge,
    val isSelected: Boolean = false,
    val isFaded: Boolean = false
  ) : PreferenceModel<Model>() {
    override fun areItemsTheSame(newItem: Model): Boolean {
      return newItem.badge.id == badge.id
    }

    override fun areContentsTheSame(newItem: Model): Boolean {
      return super.areContentsTheSame(newItem) &&
        badge == newItem.badge &&
        isSelected == newItem.isSelected &&
        isFaded == newItem.isFaded
    }

    override fun getChangePayload(newItem: Model): Any? {
      return if (badge == newItem.badge && isSelected != newItem.isSelected) {
        SELECTION_CHANGED
      } else {
        null
      }
    }
  }

  class ViewHolder(itemView: View, private val onBadgeClicked: OnBadgeClicked) : MappingViewHolder<Model>(itemView) {

    private val check: ImageView = itemView.findViewById(R.id.checkmark)
    private val badge: ImageView = itemView.findViewById(R.id.badge)
    private val name: TextView = itemView.findViewById(R.id.name)

    private var checkAnimator: ObjectAnimator? = null

    init {
      check.isSelected = true
    }

    override fun bind(model: Model) {
      itemView.setOnClickListener {
        onBadgeClicked(model.badge, model.isSelected, model.isFaded)
      }

      checkAnimator?.cancel()
      if (payload.isNotEmpty()) {
        checkAnimator = if (model.isSelected) {
          ObjectAnimator.ofFloat(check, "alpha", 1f)
        } else {
          ObjectAnimator.ofFloat(check, "alpha", 0f)
        }
        checkAnimator?.start()
        return
      }

      badge.alpha = if (model.badge.isExpired() || model.isFaded) 0.5f else 1f

      Glide.with(badge)
        .load(model.badge)
        .downsample(DownsampleStrategy.NONE)
        .diskCacheStrategy(DiskCacheStrategy.NONE)
        .transform(
          BadgeSpriteTransformation(BadgeSpriteTransformation.Size.BADGE_64, model.badge.imageDensity, ThemeUtil.isDarkTheme(context))
        )
        .into(badge)

      if (model.isSelected) {
        check.alpha = 1f
      } else {
        check.alpha = 0f
      }

      name.text = model.badge.name
    }
  }

  fun register(mappingAdapter: MappingAdapter, onBadgeClicked: OnBadgeClicked) {
    mappingAdapter.registerFactory(Model::class.java, LayoutFactory({ ViewHolder(it, onBadgeClicked) }, R.layout.badge_preference_view))
    mappingAdapter.registerFactory(EmptyModel::class.java, LayoutFactory({ EmptyViewHolder(it) }, R.layout.badge_preference_view))
  }
}
