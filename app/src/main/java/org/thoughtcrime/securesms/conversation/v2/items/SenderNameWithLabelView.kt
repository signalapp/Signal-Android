/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2.items

import android.content.Context
import android.util.AttributeSet
import androidx.annotation.ColorInt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import org.thoughtcrime.securesms.groups.memberlabel.MemberLabel
import org.thoughtcrime.securesms.groups.memberlabel.SenderNameWithLabel

/**
 * @see SenderNameWithLabel
 */
class SenderNameWithLabelView : AbstractComposeView {
  constructor(context: Context) : super(context)
  constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
  constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

  init {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
  }

  private var senderName: String by mutableStateOf("")
  private var senderColor: Color by mutableStateOf(Color.Unspecified)
  private var labelTextColor: Color by mutableStateOf(Color.Unspecified)
  private var labelBackgroundColor: Color by mutableStateOf(Color.Unspecified)

  private var memberLabel: MemberLabel? by mutableStateOf(null)

  fun setSender(name: String, @ColorInt tintColor: Int) {
    senderName = name
    senderColor = Color(tintColor)
  }

  /**
   * Sets the label with colors derived from the sender name tint color.
   */
  fun setLabel(label: MemberLabel?) {
    memberLabel = label
    labelTextColor = Color.Unspecified
    labelBackgroundColor = Color.Unspecified
  }

  /**
   * Sets the label with explicit text and background colors.
   */
  fun setLabel(label: MemberLabel?, @ColorInt textColor: Int, @ColorInt backgroundColor: Int) {
    memberLabel = label
    labelTextColor = Color(textColor)
    labelBackgroundColor = Color(backgroundColor)
  }

  /**
   * Used to update the colors in response to theme changes (e.g., wallpaper enabled/disabled).
   */
  fun updateColors(@ColorInt foregroundColor: Int, @ColorInt labelBackgroundColor: Int) {
    senderColor = Color(foregroundColor)
    labelTextColor = Color(foregroundColor)
    this.labelBackgroundColor = Color(labelBackgroundColor)
  }

  @Composable
  override fun Content() {
    if (labelTextColor != Color.Unspecified || labelBackgroundColor != Color.Unspecified) {
      SenderNameWithLabel(
        senderName = senderName,
        senderColor = senderColor,
        memberLabel = memberLabel,
        labelTextColor = labelTextColor,
        labelBackgroundColor = labelBackgroundColor
      )
    } else {
      SenderNameWithLabel(
        senderName = senderName,
        senderColor = senderColor,
        memberLabel = memberLabel
      )
    }
  }
}
