/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.signallogin.viewdetails

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.Texts
import org.signal.core.ui.compose.horizontalGutters
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.signallogin.R
import org.signal.signallogin.SignalLoginTestTags
import org.signal.signallogin.fonts.MonoTypeface

/** Size of the miniature credential card artwork shown at the top of the screen, from the design. */
private val MINI_CARD_WIDTH = 175.dp
private val MINI_CARD_HEIGHT = 100.dp

/** Corner radius of the card artwork (26dp in its 363dp-wide coordinates), scaled down to the miniature size. */
private val MINI_CARD_CORNER_RADIUS = 13.dp

private val BUTTON_MAX_WIDTH = 331.dp

private const val GROUPS_PER_ROW = 4

/** The least amount of space allowed between recovery key groups before falling back to natural text wrapping. */
private val MIN_GROUP_SPACING = 12.dp

/**
 * Shows the user the full keys that make up their Signal Login and offers ways to save them.
 */
@Composable
fun SignalLoginViewDetailsScreen(
  state: SignalLoginViewDetailsState,
  onEvent: (SignalLoginViewDetailsScreenEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  Scaffolds.Settings(
    title = stringResource(R.string.SignalLoginViewDetailsScreen__signal_login),
    onNavigationClick = { onEvent(SignalLoginViewDetailsScreenEvents.BackClicked) },
    navigationIcon = SignalIcons.ArrowStart.imageVector,
    navigationContentDescription = stringResource(R.string.SignalLoginViewDetailsScreen__navigate_back),
    modifier = modifier.testTag(SignalLoginTestTags.VIEW_DETAILS_SCREEN)
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
    ) {
      Column(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
      ) {
        MiniCard(
          modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .padding(top = 20.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Texts.SectionHeader(text = stringResource(R.string.SignalLoginViewDetailsScreen__account_key))

        KeyBlock(
          text = state.accountKey,
          onEvent = onEvent,
          modifier = Modifier.testTag(SignalLoginTestTags.VIEW_DETAILS_ACCOUNT_KEY_BLOCK)
        )

        Texts.SectionHeader(text = stringResource(R.string.SignalLoginViewDetailsScreen__recovery_key))

        RecoveryKeyBlock(
          groups = state.recoveryKeyGroups,
          onEvent = onEvent,
          modifier = Modifier.testTag(SignalLoginTestTags.VIEW_DETAILS_RECOVERY_KEY_BLOCK)
        )
      }

      Footer(onEvent = onEvent)
    }
  }
}

/**
 * A miniature of the credential card artwork, without any of the card's content.
 */
@Composable
private fun MiniCard(modifier: Modifier = Modifier) {
  Image(
    painter = painterResource(R.drawable.image_signal_login_card),
    contentDescription = null,
    contentScale = ContentScale.FillBounds,
    modifier = modifier
      .size(width = MINI_CARD_WIDTH, height = MINI_CARD_HEIGHT)
      .shadow(elevation = 6.dp, shape = RoundedCornerShape(MINI_CARD_CORNER_RADIUS))
  )
}

/**
 * A full credential rendered in the special monospace font on a rounded surface.
 */
@Composable
private fun KeyBlock(
  text: String,
  onEvent: (SignalLoginViewDetailsScreenEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  Box(modifier = modifier.keyBlockSurface(onLongClick = { onEvent(SignalLoginViewDetailsScreenEvents.AccountIdLongClicked(text)) })) {
    Text(
      text = text,
      style = keyTextStyle()
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
  groups: List<String>,
  onEvent: (SignalLoginViewDetailsScreenEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  BoxWithConstraints(modifier = modifier.keyBlockSurface(onLongClick = { onEvent(SignalLoginViewDetailsScreenEvents.RecoveryKeyLongClicked(groups.joinToString(separator = ""))) })) {
    val style = keyTextStyle()
    val textMeasurer = rememberTextMeasurer()
    val maxWidth = constraints.maxWidth

    val groupWidth = remember(groups, style) {
      groups.maxOfOrNull { group -> textMeasurer.measure(text = group, style = style).size.width } ?: 0
    }

    val minSpacing = with(LocalDensity.current) { MIN_GROUP_SPACING.roundToPx() }
    val fitsFourPerRow = groupWidth * GROUPS_PER_ROW + minSpacing * (GROUPS_PER_ROW - 1) <= maxWidth

    if (fitsFourPerRow) {
      val spacing = with(LocalDensity.current) { ((maxWidth - groupWidth * GROUPS_PER_ROW) / (GROUPS_PER_ROW - 1)).toDp() }

      Column {
        groups.chunked(GROUPS_PER_ROW).forEach { row ->
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
        text = groups.joinToString(separator = " "),
        style = style
      )
    }
  }
}

@Composable
private fun Modifier.keyBlockSurface(onLongClick: () -> Unit): Modifier {
  return this
    .horizontalGutters()
    .fillMaxWidth()
    .clip(RoundedCornerShape(18.dp))
    .background(SignalTheme.colors.colorSurface2)
    .combinedClickable(
      onLongClick = onLongClick,
      onClick = {}
    )
    .padding(horizontal = 28.dp, vertical = 20.dp)
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

@Composable
private fun Footer(onEvent: (SignalLoginViewDetailsScreenEvents) -> Unit) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(16.dp),
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 24.dp, vertical = 16.dp)
  ) {
    Buttons.MediumTonal(
      onClick = { onEvent(SignalLoginViewDetailsScreenEvents.SaveToPasswordManagerClicked) },
      colors = ButtonDefaults.filledTonalButtonColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
      ),
      modifier = Modifier
        .widthIn(max = BUTTON_MAX_WIDTH)
        .fillMaxWidth()
        .testTag(SignalLoginTestTags.VIEW_DETAILS_SAVE_TO_PASSWORD_MANAGER_BUTTON)
    ) {
      Text(stringResource(R.string.SignalLoginViewDetailsScreen__save_to_password_manager))
    }

    Buttons.MediumTonal(
      onClick = { onEvent(SignalLoginViewDetailsScreenEvents.SaveAsPdfClicked) },
      colors = ButtonDefaults.filledTonalButtonColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
      ),
      modifier = Modifier
        .widthIn(max = BUTTON_MAX_WIDTH)
        .fillMaxWidth()
        .testTag(SignalLoginTestTags.VIEW_DETAILS_SAVE_AS_PDF_BUTTON)
    ) {
      Text(stringResource(R.string.SignalLoginViewDetailsScreen__save_as_pdf))
    }
  }
}

@DayNightPreviews
@Composable
private fun SignalLoginViewDetailsScreenPreview() {
  Previews.Preview {
    SignalLoginViewDetailsScreen(
      state = SignalLoginViewDetailsState(
        accountKey = "A6B28482-2E32-83D0-7F23-91360A4C2B91",
        recoveryKey = "UY38JH2778HJJHJ8LK19GA61S672JSJ=89R=23S6A578=9BAP92J2YH5T326VV7T"
      ),
      onEvent = {}
    )
  }
}
