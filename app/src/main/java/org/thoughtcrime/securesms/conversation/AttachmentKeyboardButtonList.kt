/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.ButtonStripItemView
import java.util.function.Consumer

class AttachmentKeyboardButtonList @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0) : HorizontalScrollView(context, attrs, defStyleAttr) {

  private val inner: LinearLayout
  var onButtonClicked: Consumer<AttachmentKeyboardButton> = Consumer { _ -> }

  init {
    inflate(context, R.layout.attachment_keyboard_button_list, this)
    inner = findViewById(R.id.attachment_keyboard_button_list_inner_linearlayout)
  }

  fun setButtons(newButtons: List<AttachmentKeyboardButton>) {
    inner.removeAllViews()
    newButtons.asSequence().map { buttonToView(it) }.forEach { inner.addView(it) }
    AttachmentButtonCenterHelper.recenter(inner)
  }

  private fun buttonToView(button: AttachmentKeyboardButton): View {
    return (LayoutInflater.from(context).inflate(R.layout.attachment_keyboard_button_item, inner, false) as ButtonStripItemView).apply {
      fillFromButton(button)
      setOnClickListener { onButtonClicked.accept(button) }
    }
  }
}
