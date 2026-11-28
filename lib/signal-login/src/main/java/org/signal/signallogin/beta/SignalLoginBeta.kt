/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.signallogin.beta

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.core.ui.fonts.SignalSymbols
import org.signal.signallogin.R
import org.signal.signallogin.SignalLoginTestTags

/**
 * Pill that marks Signal Login as an unfinished feature. Sits beside the title of every screen that is part of the
 * feature.
 */
@Composable
fun SignalLoginBetaTag(modifier: Modifier = Modifier) {
  Text(
    text = stringResource(R.string.SignalLoginBeta__beta),
    style = MaterialTheme.typography.labelSmall,
    color = MaterialTheme.colorScheme.onSurface,
    modifier = modifier
      .testTag(SignalLoginTestTags.BETA_TAG)
      .clip(CircleShape)
      .background(SignalTheme.colors.colorSurface4)
      .padding(horizontal = 12.dp, vertical = 4.dp)
  )
}

/**
 * Longer form of [SignalLoginBetaTag], for the places that have room to warn the user that the feature is still
 * changing underneath them.
 */
@Composable
fun SignalLoginBetaDisclaimer(
  modifier: Modifier = Modifier,
  textAlign: TextAlign = TextAlign.Start
) {
  Text(
    text = SignalSymbols.signalSymbolText(
      text = stringResource(R.string.SignalLoginBeta__this_is_a_beta_feature_that_will_be_updated),
      glyphStart = SignalSymbols.Glyph.INFO
    ),
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign = textAlign,
    modifier = modifier.testTag(SignalLoginTestTags.BETA_DISCLAIMER)
  )
}

@DayNightPreviews
@Composable
private fun SignalLoginBetaPreview() {
  Previews.Preview {
    Column(
      verticalArrangement = Arrangement.spacedBy(16.dp),
      modifier = Modifier.padding(16.dp)
    ) {
      SignalLoginBetaTag()

      SignalLoginBetaDisclaimer()
    }
  }
}
