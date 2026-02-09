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
  private var memberLabel: MemberLabel? by mutableStateOf(null)

  fun setSender(name: String, @ColorInt tintColor: Int) {
    senderName = name
    senderColor = Color(tintColor)
  }

  fun setLabel(label: MemberLabel?) {
    memberLabel = label
  }

  @Composable
  override fun Content() {
    SenderNameWithLabel(
      senderName = senderName,
      senderColor = senderColor,
      label = memberLabel
    )
  }
}
