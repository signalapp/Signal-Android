/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.signallogin.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.Texts
import org.signal.core.ui.compose.horizontalGutters
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.signallogin.R
import org.signal.signallogin.RecoveryKeyGroups
import org.signal.signallogin.SignalLoginTestTags
import org.signal.signallogin.fonts.MonoTypeface

private const val GROUPS_PER_ROW = 4

/** The least amount of space allowed between recovery key groups before falling back to natural text wrapping. */
private val MIN_GROUP_SPACING = 12.dp

private val KEY_BLOCK_TEXT_PADDING_HORIZONTAL = 28.dp
private val KEY_BLOCK_TEXT_PADDING_VERTICAL = 20.dp

/** Insets that center the copy button's icon on the first line of key text and put it 16dp from the block's end edge, accounting for the button's own 12dp of internal padding. */
private val COPY_BUTTON_PADDING_TOP = 10.dp
private val COPY_BUTTON_PADDING_END = 4.dp

/**
 * Both halves of a Signal Login spelled out in full -- the account ID and the recovery key -- each under a section
 * header and each with a button that copies it to the clipboard.
 *
 * Shared by every screen that shows the user their keys, so the screens themselves only supply the surrounding
 * chrome: headers, footers and whatever buttons that particular step of the flow needs.
 */
@Composable
fun SignalLoginKeyDetails(
  accountId: String,
  recoveryKeyGroups: RecoveryKeyGroups,
  onCopyAccountId: (String) -> Unit,
  onCopyRecoveryKey: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  Column(modifier = modifier) {
    Texts.SectionHeader(text = stringResource(R.string.SignalLoginKeyDetails__account_id))

    AccountIdBlock(
      text = accountId,
      onCopyAccountId = onCopyAccountId,
      modifier = Modifier.testTag(SignalLoginTestTags.KEY_DETAILS_ACCOUNT_ID_BLOCK)
    )

    Texts.SectionHeader(text = stringResource(R.string.SignalLoginKeyDetails__recovery_key))

    RecoveryKeyBlock(
      groups = recoveryKeyGroups,
      onCopyRecoveryKey = onCopyRecoveryKey,
      modifier = Modifier.testTag(SignalLoginTestTags.KEY_DETAILS_RECOVERY_KEY_BLOCK)
    )
  }
}

/**
 * A full credential rendered in the special monospace font on a rounded surface.
 */
@Composable
private fun AccountIdBlock(
  text: String,
  onCopyAccountId: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  Row(
    verticalAlignment = Alignment.Top,
    modifier = modifier.keyBlockSurface()
  ) {
    Text(
      text = text,
      style = keyTextStyle(),
      modifier = Modifier
        .weight(1f)
        .padding(start = KEY_BLOCK_TEXT_PADDING_HORIZONTAL, top = KEY_BLOCK_TEXT_PADDING_VERTICAL, bottom = KEY_BLOCK_TEXT_PADDING_VERTICAL)
    )

    CopyButton(
      contentDescription = stringResource(R.string.SignalLoginKeyDetails__copy_account_id),
      onClick = { onCopyAccountId(text) },
      modifier = Modifier.testTag(SignalLoginTestTags.KEY_DETAILS_ACCOUNT_ID_COPY_BUTTON)
    )
  }
}

/**
 * The recovery key rendered as character groups. When four groups fit per row with at least
 * [MIN_GROUP_SPACING] between them, renders rows of four groups evenly spaced across the full
 * width. Otherwise renders the whole key as a single space-separated string that wraps naturally.
 */
@Composable
private fun RecoveryKeyBlock(
  groups: RecoveryKeyGroups,
  onCopyRecoveryKey: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  Row(
    verticalAlignment = Alignment.Top,
    modifier = modifier.keyBlockSurface()
  ) {
    BoxWithConstraints(
      modifier = Modifier
        .weight(1f)
        .padding(start = KEY_BLOCK_TEXT_PADDING_HORIZONTAL, top = KEY_BLOCK_TEXT_PADDING_VERTICAL, bottom = KEY_BLOCK_TEXT_PADDING_VERTICAL)
    ) {
      val style = keyTextStyle()
      val textMeasurer = rememberTextMeasurer()
      val maxWidth = constraints.maxWidth

      val groupWidth = remember(groups, style) {
        groups.groups.maxOfOrNull { group -> textMeasurer.measure(text = group, style = style).size.width } ?: 0
      }

      val minSpacing = with(LocalDensity.current) { MIN_GROUP_SPACING.roundToPx() }
      val fitsFourPerRow = groupWidth * GROUPS_PER_ROW + minSpacing * (GROUPS_PER_ROW - 1) <= maxWidth

      if (fitsFourPerRow) {
        val spacing = with(LocalDensity.current) { ((maxWidth - groupWidth * GROUPS_PER_ROW) / (GROUPS_PER_ROW - 1)).toDp() }

        Column {
          groups.rows(GROUPS_PER_ROW).forEach { row ->
            Row(
              horizontalArrangement = Arrangement.spacedBy(spacing),
              modifier = Modifier.fillMaxWidth()
            ) {
              row.forEach { group ->
                Text(
                  text = group,
                  style = style
                )
              }
            }
          }
        }
      } else {
        Text(
          text = groups.spaced,
          style = style
        )
      }
    }

    CopyButton(
      contentDescription = stringResource(R.string.SignalLoginKeyDetails__copy_recovery_key),
      onClick = { onCopyRecoveryKey(groups.flat) },
      modifier = Modifier.testTag(SignalLoginTestTags.KEY_DETAILS_RECOVERY_KEY_COPY_BUTTON)
    )
  }
}

/**
 * The button in the top-end corner of a key block that copies the key to the clipboard.
 */
@Composable
private fun CopyButton(
  contentDescription: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  IconButton(
    onClick = onClick,
    modifier = modifier.padding(top = COPY_BUTTON_PADDING_TOP, end = COPY_BUTTON_PADDING_END)
  ) {
    Icon(
      painter = SignalIcons.Copy.painter,
      contentDescription = contentDescription,
      tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}

@Composable
private fun Modifier.keyBlockSurface(): Modifier {
  return this
    .horizontalGutters()
    .fillMaxWidth()
    .clip(RoundedCornerShape(18.dp))
    .background(SignalTheme.colors.colorSurface2)
}

@Composable
private fun keyTextStyle(): TextStyle {
  return MaterialTheme.typography.bodyLarge.copy(
    fontFamily = MonoTypeface.fontFamily(),
    fontSize = 18.sp,
    lineHeight = 28.sp,
    letterSpacing = 1.44.sp
  )
}

@DayNightPreviews
@Composable
private fun SignalLoginKeyDetailsPreview() {
  Previews.Preview {
    SignalLoginKeyDetails(
      accountId = "A6B28482-2E32-83D0-7F23-91360A4C2B91",
      recoveryKeyGroups = RecoveryKeyGroups.from("UY38JH2778HJJHJ8LK19GA61S672JSJ=89R=23S6A578=9BAP92J2YH5T326VV7T"),
      onCopyAccountId = {},
      onCopyRecoveryKey = {}
    )
  }
}
