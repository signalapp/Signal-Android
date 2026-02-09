/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.groups.memberlabel

import android.content.Context
import android.util.AttributeSet
import androidx.annotation.ColorInt
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * @see MemberLabelPill
 */
class MemberLabelPillView : AbstractComposeView {
  constructor(context: Context) : super(context)
  constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
  constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

  private var memberLabel: MemberLabel? by mutableStateOf(null)
  private var tintColor: Color by mutableStateOf(Color.Unspecified)

  var style: Style by mutableStateOf(Style())

  fun setLabel(label: MemberLabel, tintColor: Color) {
    this.memberLabel = label
    this.tintColor = tintColor
  }

  fun setLabel(label: MemberLabel, @ColorInt tintColor: Int) {
    this.memberLabel = label
    this.tintColor = Color(tintColor)
  }

  @Composable
  override fun Content() {
    memberLabel?.let { label ->
      MemberLabelPill(
        emoji = label.emoji,
        text = label.text,
        tintColor = tintColor,
        modifier = Modifier.padding(horizontal = style.horizontalPadding, vertical = style.verticalPadding),
        textStyle = style.textStyle()
      )
    }
  }

  data class Style(
    val horizontalPadding: Dp = 12.dp,
    val verticalPadding: Dp = 2.dp,
    val textStyle: @Composable () -> TextStyle = { MaterialTheme.typography.bodyLarge }
  )
}
