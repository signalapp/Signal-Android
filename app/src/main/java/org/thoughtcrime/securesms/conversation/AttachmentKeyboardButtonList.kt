/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import androidx.core.view.plusAssign
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.ButtonStripItemView
import org.signal.core.util.logging.Log
import java.util.function.Consumer

class AttachmentKeyboardButtonList @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0) : HorizontalScrollView(context, attrs, defStyleAttr) {

  companion object {
    val TAG = Log.tag(AttachmentKeyboardButtonList::class)
  }

  private val inflater = LayoutInflater.from(context)
  private val inner: LinearLayout
  private val recenterOnWidthChange: OnLayoutChangeListener
  var onButtonClicked: Consumer<AttachmentKeyboardButton> = Consumer { _ -> }

  private var currentButtons: List<AttachmentKeyboardButton> = listOf()

  init {
    inflate(context, R.layout.attachment_keyboard_button_list, this)
    inner = findViewById(R.id.attachment_keyboard_button_list_inner_linearlayout)
    recenterOnWidthChange = OnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
      if (oldRight - oldLeft == right - left)
        return@OnLayoutChangeListener
      AttachmentButtonCenterHelper.recenter(inner, this)
    }
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    addOnLayoutChangeListener(recenterOnWidthChange)
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    removeOnLayoutChangeListener(recenterOnWidthChange)
  }

  fun setButtons(newButtons: List<AttachmentKeyboardButton>) {
    if (currentButtons == newButtons)
      return
    Log.i(TAG, "setButtons: $currentButtons -> $newButtons")
    currentButtons = newButtons
    inner.removeAllViews()
    newButtons.forEach { inner += inflateButton(it) }
    inner.post { AttachmentButtonCenterHelper.recenter(inner, this) }
  }

  private fun inflateButton(button: AttachmentKeyboardButton): ButtonStripItemView {
    val buttonView = inflater.inflate(R.layout.attachment_keyboard_button_item, inner, false) as ButtonStripItemView
    return buttonView.apply {
      fillFromButton(button)
      setOnClickListener { onButtonClicked.accept(button) }
    }
  }
}
