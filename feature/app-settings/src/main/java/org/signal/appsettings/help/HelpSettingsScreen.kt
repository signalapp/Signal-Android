/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.help

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.tooling.preview.PreviewWrapper
import org.signal.appsettings.R
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Rows.TextAndLabel
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.SignalPreviewWrapper
import org.signal.core.ui.R as CoreUiR

@VisibleForTesting
object HelpSettingsTestTags {
  const val SCROLLER = "scroller"
  const val ROW_SUPPORT_CENTER = "row-support-center"
  const val ROW_CONTACT_US = "row-contact-us"
  const val ROW_VERSION = "row-version"
  const val ROW_DEBUG_LOG = "row-debug-log"
  const val ROW_LICENSES = "row-licenses"
  const val ROW_TERMS_AND_PRIVACY_POLICY = "row-terms-and-privacy-policy"
}

/**
 * Links to support, the app version, debug logs, and legal information.
 */
@Composable
fun HelpSettingsScreen(
  state: HelpSettingsState,
  onEvent: (HelpSettingsEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  Scaffolds.Settings(
    title = stringResource(R.string.preferences__help),
    onNavigationClick = { onEvent(HelpSettingsEvent.NavigateBackClicked) },
    navigationIcon = SignalIcons.ArrowStart.imageVector,
    navigationContentDescription = stringResource(CoreUiR.string.CloseButton__content_description),
    modifier = modifier
  ) { contentPadding ->
    LazyColumn(
      modifier = Modifier
        .padding(contentPadding)
        .testTag(HelpSettingsTestTags.SCROLLER)
    ) {
      item {
        Rows.LinkRow(
          text = stringResource(R.string.HelpSettingsFragment__support_center),
          icon = ImageVector.vectorResource(CoreUiR.drawable.symbol_open_20),
          onClick = { onEvent(HelpSettingsEvent.SupportCenterClicked) },
          modifier = Modifier.testTag(HelpSettingsTestTags.ROW_SUPPORT_CENTER)
        )
      }

      item {
        Rows.TextRow(
          text = stringResource(R.string.HelpSettingsFragment__contact_us),
          onClick = { onEvent(HelpSettingsEvent.ContactUsClicked) },
          modifier = Modifier.testTag(HelpSettingsTestTags.ROW_CONTACT_US)
        )
      }

      item {
        Dividers.Default()
      }

      item {
        Rows.TextRow(
          text = stringResource(R.string.HelpSettingsFragment__version),
          label = state.versionName,
          onLongClick = { onEvent(HelpSettingsEvent.VersionLongClicked) },
          modifier = Modifier.testTag(HelpSettingsTestTags.ROW_VERSION)
        )
      }

      item {
        Rows.TextRow(
          text = stringResource(R.string.HelpSettingsFragment__debug_log),
          onClick = { onEvent(HelpSettingsEvent.DebugLogClicked) },
          modifier = Modifier.testTag(HelpSettingsTestTags.ROW_DEBUG_LOG)
        )
      }

      item {
        Rows.TextRow(
          text = stringResource(R.string.HelpSettingsFragment__licenses),
          onClick = { onEvent(HelpSettingsEvent.LicensesClicked) },
          modifier = Modifier.testTag(HelpSettingsTestTags.ROW_LICENSES)
        )
      }

      item {
        Rows.LinkRow(
          text = stringResource(R.string.HelpSettingsFragment__terms_amp_privacy_policy),
          textStyle = MaterialTheme.typography.bodyLarge.copy(hyphens = Hyphens.Auto),
          icon = ImageVector.vectorResource(CoreUiR.drawable.symbol_open_20),
          onClick = { onEvent(HelpSettingsEvent.TermsAndPrivacyPolicyClicked) },
          modifier = Modifier.testTag(HelpSettingsTestTags.ROW_TERMS_AND_PRIVACY_POLICY)
        )
      }

      item {
        LegalNotice()
      }
    }
  }
}

@Composable
private fun LegalNotice() {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(Rows.defaultPadding()),
    verticalAlignment = Alignment.CenterVertically
  ) {
    TextAndLabel(
      label = listOf(
        stringResource(R.string.HelpFragment__copyright_signal_messenger),
        stringResource(R.string.HelpFragment__licenced_under_the_agplv3),
        stringResource(R.string.HelpSettingsFragment__signal_is_a_501c3)
      ).joinToString("\n")
    )
  }
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun HelpSettingsScreenPreview() {
  HelpSettingsScreen(
    state = HelpSettingsState(versionName = "8.33.0"),
    onEvent = {}
  )
}
