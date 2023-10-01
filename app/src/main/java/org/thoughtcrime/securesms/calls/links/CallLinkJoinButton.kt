/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.calls.links

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.annotation.ColorRes
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import org.thoughtcrime.securesms.R

/**
 * ConversationItem action button for joining a call link.
 */
class CallLinkJoinButton @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : LinearLayoutCompat(context, attrs) {
  init {
    orientation = VERTICAL
    inflate(context, R.layout.call_link_join_button, this)
  }

  private val joinStroke: View = findViewById(R.id.join_stroke)
  private val joinButton: MaterialButton = findViewById(R.id.join_button)

  fun setTextColor(@ColorRes textColorResId: Int) {
    val color = ContextCompat.getColor(context, textColorResId)

    joinButton.setTextColor(color)
  }

  fun setStrokeColor(@ColorRes strokeColorResId: Int) {
    val color = ContextCompat.getColor(context, strokeColorResId)

    joinStroke.setBackgroundColor(color)
  }

  fun setJoinClickListener(onClickListener: OnClickListener) {
    joinButton.setOnClickListener(onClickListener)
  }
}
