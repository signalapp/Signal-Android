/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.mediasend.v2.review

import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.TextView
import org.thoughtcrime.securesms.R
import kotlin.time.Duration.Companion.seconds

/**
 * Toast-style notification used in the media review flow. This exists so we can specify the location and animation of how it appears.
 */
class MediaReviewToastPopupWindow private constructor(parent: ViewGroup, iconResource: Int, descriptionText: String) : PopupWindow(
  LayoutInflater.from(parent.context).inflate(R.layout.v2_media_review_quality_popup_window, parent, false),
  ViewGroup.LayoutParams.WRAP_CONTENT,
  ViewGroup.LayoutParams.WRAP_CONTENT
) {

  private val icon: ImageView = contentView.findViewById(R.id.media_review_toast_popup_icon)
  private val description: TextView = contentView.findViewById(R.id.media_review_toast_popup_description)

  init {
    animationStyle = R.style.StickerPopupAnimation
    icon.setImageResource(iconResource)
    description.text = descriptionText
  }

  private fun show(parent: ViewGroup) {
    showAtLocation(parent, Gravity.CENTER, 0, 0)
    contentView.postDelayed({ dismiss() }, DURATION)
  }

  companion object {
    private val DURATION = 3.seconds.inWholeMilliseconds

    @JvmStatic
    fun show(parent: ViewGroup, icon: Int, description: String): MediaReviewToastPopupWindow {
      val qualityToast = MediaReviewToastPopupWindow(parent, icon, description)
      qualityToast.show(parent)
      return qualityToast
    }
  }
}
