/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.help

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import org.signal.appsettings.AppSettingsLazyColumn
import org.signal.appsettings.R
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.SignalPreviewWrapper
import org.signal.core.ui.R as CoreUiR

@VisibleForTesting
object LicenseTestTags {
  const val SCROLLER = "scroller"
}

/**
 * The licenses of the open-source software the app is built on.
 */
@Composable
fun LicenseScreen(
  state: LicenseState,
  onEvent: (LicenseEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  Scaffolds.Settings(
    title = stringResource(R.string.HelpSettingsFragment__licenses),
    onNavigationClick = { onEvent(LicenseEvent.NavigateBackClicked) },
    navigationIcon = SignalIcons.ArrowStart.imageVector,
    navigationContentDescription = stringResource(CoreUiR.string.CloseButton__content_description),
    modifier = modifier
  ) { contentPadding ->
    AppSettingsLazyColumn(
      scaffoldPadding = contentPadding,
      contentPadding = PaddingValues(horizontal = 4.dp),
      modifier = Modifier.testTag(LicenseTestTags.SCROLLER)
    ) {
      items(state.licenseLines) { line ->
        Text(
          text = line,
          style = MaterialTheme.typography.bodyMedium
        )
      }
    }
  }
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun LicenseScreenPreview() {
  LicenseScreen(
    state = LicenseState(
      licenseLines = listOf(
        "Copyright (C) 2007 Free Software Foundation, Inc.",
        "",
        "Everyone is permitted to copy and distribute verbatim copies of this license document, but changing it is not allowed."
      )
    ),
    onEvent = {}
  )
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun LicenseScreenLoadingPreview() {
  LicenseScreen(state = LicenseState(), onEvent = {})
}
