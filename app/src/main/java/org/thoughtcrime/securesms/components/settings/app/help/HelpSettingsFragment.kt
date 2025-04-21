/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.help

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Rows.TextAndLabel
import org.signal.core.ui.compose.Rows.defaultPadding
import org.signal.core.ui.compose.Scaffolds
import org.thoughtcrime.securesms.BuildConfig
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.navigation.safeNavigate

class HelpSettingsFragment : ComposeFragment() {

  @Composable
  override fun FragmentContent() {
    val navController: NavController = remember { findNavController() }

    val context = LocalContext.current

    Scaffolds.Settings(
      title = stringResource(R.string.preferences__help),
      onNavigationClick = { navController.popBackStack() },
      navigationIconPainter = painterResource(id = R.drawable.ic_arrow_left_24),
      navigationContentDescription = stringResource(id = R.string.Material3SearchToolbar__close)
    ) { contentPadding ->
      LazyColumn(
        modifier = Modifier.padding(contentPadding)
      ) {
        item {
          Rows.LinkRow(
            text = stringResource(R.string.HelpSettingsFragment__support_center),
            icon = ImageVector.vectorResource(R.drawable.symbol_open_20),
            onClick = {
              CommunicationActions.openBrowserLink(context, getString(R.string.support_center_url))
            }
          )
        }

        item {
          Rows.TextRow(
            text = stringResource(id = R.string.HelpSettingsFragment__contact_us),
            onClick = {
              navController.safeNavigate(R.id.action_helpSettingsFragment_to_helpFragment)
            }
          )
        }

        item {
          Dividers.Default()
        }

        item {
          Rows.TextRow(
            text = stringResource(R.string.HelpSettingsFragment__version),
            label = BuildConfig.VERSION_NAME
          )
        }

        item {
          Rows.TextRow(
            text = stringResource(id = R.string.HelpSettingsFragment__debug_log),
            onClick = {
              navController.safeNavigate(R.id.action_helpSettingsFragment_to_submitDebugLogActivity)
            }
          )
        }

        item {
          Rows.TextRow(
            text = stringResource(id = R.string.HelpSettingsFragment__licenses),
            onClick = {
              navController.safeNavigate(R.id.action_helpSettingsFragment_to_licenseFragment)
            }
          )
        }

        item {
          Rows.LinkRow(
            text = stringResource(R.string.HelpSettingsFragment__terms_amp_privacy_policy),
            icon = ImageVector.vectorResource(R.drawable.symbol_open_20),
            onClick = {
              CommunicationActions.openBrowserLink(context, getString(R.string.terms_and_privacy_policy_url))
            }
          )
        }

        item {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(defaultPadding()),
            verticalAlignment = CenterVertically
          ) {
            TextAndLabel(
              label = StringBuilder().apply {
                append(getString(R.string.HelpFragment__copyright_signal_messenger))
                append("\n")
                append(getString(R.string.HelpFragment__licenced_under_the_agplv3))
              }.toString()
            )
          }
        }
      }
    }
  }
}
