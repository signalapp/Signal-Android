/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.registration

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.signal.core.ui.compose.theme.SignalTheme

private const val DEFAULT_CODE_LENGTH: Int = 6
private val BOX_SPACING = 4.dp
private val MIN_BOX_WIDTH = 24.dp
private val SEPARATOR_FONT_SIZE = 28.sp
private val BOX_CORNER_RADIUS = 4.dp
private val BOTTOM_ACCENT_HEIGHT = 2.dp
private const val DASH_INSERT_INDEX: Int = 2
private const val DASH_MIN_CODE_LENGTH: Int = 3
private const val BOX_HEIGHT_NUMERATOR: Float = 7f
private const val BOX_HEIGHT_DENOMINATOR: Float = 6f

@Composable
fun VerificationCodeViewCompose(
  codeLength: Int = DEFAULT_CODE_LENGTH,
  onCodeComplete: (String) -> Unit,
  codeState: List<String>
) {
  val focusManager = LocalFocusManager.current

  val firstEmptyIndex by remember(codeState, codeLength) { derivedStateOf { nextEmptyIndex(codeState, codeLength) } }
  var focusedIndex by remember { mutableStateOf(firstEmptyIndex) }

  val onComplete by rememberUpdatedState(onCodeComplete)

  val codeString by remember(codeState) { derivedStateOf { codeState.joinToString("") } }

  LaunchedEffect(firstEmptyIndex) { focusManager.clearFocus() }

  LaunchedEffect(codeString) {
    focusedIndex = nextEmptyIndex(codeState, codeLength)

    if (codeState.all { it.length == 1 }) {
      onComplete(codeString)
      focusManager.clearFocus()
    }
  }

  SignalTheme {
    val boxSpacing = BOX_SPACING

    BoxWithConstraints {
      // Use the full maxWidth so there are no absolute start/end margins.
      val availableWidth = this.maxWidth

      val textMeasurer = rememberTextMeasurer()
      val density = LocalDensity.current
      val separatorWidth = remember(textMeasurer, density) {
        val layout = textMeasurer.measure(AnnotatedString("-"), style = TextStyle(fontSize = SEPARATOR_FONT_SIZE))
        density.run { layout.size.width.toDp() }
      }

      val hasSeparator = codeLength > DASH_MIN_CODE_LENGTH
      val elementsCount = if (hasSeparator) codeLength + 1 else codeLength
      val totalGaps = (elementsCount - 1)
      val totalSpacing = boxSpacing * totalGaps

      val availableForBoxes = availableWidth - totalSpacing - if (hasSeparator) separatorWidth else 0.dp
      val computedBoxWidth = availableForBoxes / codeLength
      val boxWidth = computedBoxWidth.coerceAtLeast(MIN_BOX_WIDTH)
      val boxHeight = boxWidth * BOX_HEIGHT_NUMERATOR / BOX_HEIGHT_DENOMINATOR

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(boxSpacing, Alignment.CenterHorizontally)
      ) {
        for (i in 0 until codeLength) {
          val char = codeState.getOrNull(i) ?: ""
          val isFocused = focusedIndex == i

          DigitCell(
            char = char,
            isFocused = isFocused,
            boxWidth = boxWidth,
            boxHeight = boxHeight,
            onClick = { focusedIndex = i }
          )

          if (i == DASH_INSERT_INDEX && hasSeparator) {
            Text(
              text = "-",
              style = TextStyle(fontSize = SEPARATOR_FONT_SIZE, color = MaterialTheme.colorScheme.onSurfaceVariant),
              modifier = Modifier
            )
          }
        }
      }
    }
  }
}

@Composable
private fun DigitCell(
  char: String,
  isFocused: Boolean,
  boxWidth: Dp,
  boxHeight: Dp,
  onClick: () -> Unit
) {
  Box(
    modifier = Modifier
      .size(width = boxWidth, height = boxHeight)
      .clickable { onClick() }
      .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(topStart = BOX_CORNER_RADIUS, topEnd = BOX_CORNER_RADIUS, bottomEnd = 0.dp, bottomStart = 0.dp)),
    contentAlignment = Alignment.Center
  ) {
    if (char.isEmpty()) {
      Text("•", style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)), textAlign = TextAlign.Center)
    } else {
      Text(char, style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurfaceVariant), textAlign = TextAlign.Center)
    }

    Box(
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .height(BOTTOM_ACCENT_HEIGHT)
        .fillMaxWidth()
        .background(MaterialTheme.colorScheme.primary.copy(alpha = if (isFocused) 1f else 0f))
    )
  }
}

private fun nextEmptyIndex(codeState: List<String>, codeLength: Int): Int {
  val idx = codeState.indexOfFirst { it.isEmpty() }
  return if (idx == -1) codeLength - 1 else idx
}

@PreviewFontScale
@Composable
fun VerificationCodeViewComposePreview() {
  SignalTheme {
    VerificationCodeViewCompose(
      codeLength = DEFAULT_CODE_LENGTH,
      codeState = listOf("1", "5", "6", "", "", ""),
      onCodeComplete = {}
    )
  }
}
