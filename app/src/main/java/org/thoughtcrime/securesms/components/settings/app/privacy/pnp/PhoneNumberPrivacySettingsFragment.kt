package org.thoughtcrime.securesms.components.settings.app.privacy.pnp

import android.content.res.Configuration
import android.os.Bundle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import org.signal.core.ui.Dividers
import org.signal.core.ui.Rows
import org.signal.core.ui.Scaffolds
import org.signal.core.ui.Texts
import org.signal.core.ui.theme.SignalTheme
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.compose.StatusBarColorNestedScrollConnection
import org.signal.core.ui.R as CoreUiR

class PhoneNumberPrivacySettingsFragment : ComposeFragment() {

  private val viewModel: PhoneNumberPrivacySettingsViewModel by viewModels()
  private lateinit var statusBarNestedScrollConnection: StatusBarColorNestedScrollConnection

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    statusBarNestedScrollConnection = StatusBarColorNestedScrollConnection(requireActivity())
  }

  override fun onResume() {
    super.onResume()
    statusBarNestedScrollConnection.setColorImmediate()
  }

  @Composable
  override fun FragmentContent() {
    val state: PhoneNumberPrivacySettingsState by viewModel.state
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarMessage = stringResource(id = R.string.PhoneNumberPrivacySettingsFragment__to_change_this_setting)

    Screen(
      state = state,
      snackbarHostState = snackbarHostState,
      onNavigationClick = { findNavController().popBackStack() },
      statusBarNestedScrollConnection = statusBarNestedScrollConnection,
      onEveryoneCanSeeMyNumberClicked = viewModel::setEveryoneCanSeeMyNumber,
      onNobodyCanSeeMyNumberClicked = viewModel::setNobodyCanSeeMyNumber,
      onEveryoneCanFindMeByNumberClicked = viewModel::setEveryoneCanFindMeByMyNumber,
      onNobodyCanFindMeByNumberClicked = {
        if (!state.phoneNumberSharing) {
          onNobodyCanFindMeByNumberClicked()
        } else {
          lifecycleScope.launch {
            snackbarHostState.showSnackbar(
              message = snackbarMessage,
              duration = SnackbarDuration.Short
            )
          }
        }
      }
    )
  }

  private fun onNobodyCanFindMeByNumberClicked() {
    MaterialAlertDialogBuilder(requireContext())
      .setTitle(R.string.PhoneNumberPrivacySettingsFragment__nobody_can_find_me_warning_title)
      .setMessage(getString(R.string.PhoneNumberPrivacySettingsFragment__nobody_can_find_me_warning_message))
      .setNegativeButton(getString(R.string.PhoneNumberPrivacySettingsFragment__cancel), null)
      .setPositiveButton(android.R.string.ok) { _, _ -> viewModel.setNobodyCanFindMeByMyNumber() }
      .show()
  }
}

@Composable
private fun Screen(
  state: PhoneNumberPrivacySettingsState,
  snackbarHostState: SnackbarHostState = SnackbarHostState(),
  onNavigationClick: () -> Unit = {},
  statusBarNestedScrollConnection: StatusBarColorNestedScrollConnection? = null,
  onEveryoneCanSeeMyNumberClicked: () -> Unit = {},
  onNobodyCanSeeMyNumberClicked: () -> Unit = {},
  onEveryoneCanFindMeByNumberClicked: () -> Unit = {},
  onNobodyCanFindMeByNumberClicked: () -> Unit = {}
) {
  Scaffolds.Settings(
    title = stringResource(id = R.string.preferences_app_protection__phone_number),
    onNavigationClick = onNavigationClick,
    navigationIconPainter = painterResource(id = R.drawable.ic_arrow_left_24),
    navigationContentDescription = stringResource(id = R.string.Material3SearchToolbar__close),
    snackbarHost = {
      SnackbarHost(snackbarHostState)
    },
    modifier = statusBarNestedScrollConnection?.let { Modifier.nestedScroll(it) } ?: Modifier
  ) { contentPadding ->
    Box(modifier = Modifier.padding(contentPadding)) {
      LazyColumn {
        item {
          Texts.SectionHeader(
            text = stringResource(id = R.string.PhoneNumberPrivacySettingsFragment_who_can_see_my_number_heading)
          )
        }

        item {
          Rows.RadioRow(
            selected = state.phoneNumberSharing,
            text = stringResource(id = R.string.PhoneNumberPrivacy_everyone),
            modifier = Modifier.clickable(onClick = onEveryoneCanSeeMyNumberClicked)
          )
        }

        item {
          Rows.RadioRow(
            selected = !state.phoneNumberSharing,
            text = stringResource(id = R.string.PhoneNumberPrivacy_nobody),
            modifier = Modifier.clickable(onClick = onNobodyCanSeeMyNumberClicked)
          )
        }

        item {
          Text(
            text = stringResource(
              id = if (state.phoneNumberSharing) {
                R.string.PhoneNumberPrivacySettingsFragment_sharing_on_description
              } else {
                if (state.discoverableByPhoneNumber) {
                  R.string.PhoneNumberPrivacySettingsFragment_sharing_off_discovery_on_description
                } else {
                  R.string.PhoneNumberPrivacySettingsFragment_sharing_off_discovery_off_description
                }
              }
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = dimensionResource(id = CoreUiR.dimen.gutter), vertical = 16.dp)
          )
        }

        item {
          Dividers.Default()
        }

        item {
          Texts.SectionHeader(text = stringResource(id = R.string.PhoneNumberPrivacySettingsFragment_who_can_find_me_by_number_heading))
        }

        item {
          Rows.RadioRow(
            selected = state.discoverableByPhoneNumber,
            text = stringResource(id = R.string.PhoneNumberPrivacy_everyone),
            modifier = Modifier.clickable(onClick = onEveryoneCanFindMeByNumberClicked)
          )
        }

        item {
          Rows.RadioRow(
            enabled = !state.phoneNumberSharing,
            selected = !state.discoverableByPhoneNumber,
            text = stringResource(id = R.string.PhoneNumberPrivacy_nobody),
            modifier = Modifier.clickable(onClick = onNobodyCanFindMeByNumberClicked)
          )
        }

        item {
          Text(
            text = stringResource(
              id = if (state.discoverableByPhoneNumber) {
                R.string.PhoneNumberPrivacySettingsFragment_discovery_on_description
              } else {
                R.string.PhoneNumberPrivacySettingsFragment_discovery_off_description
              }
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = dimensionResource(id = CoreUiR.dimen.gutter), vertical = 16.dp)
          )
        }
      }
    }
  }
}

@Preview(name = "Light Theme", group = "Screen", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark Theme", group = "Screen", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ScreenPreviewSharingAndDiscoverable() {
  SignalTheme {
    Screen(
      state = PhoneNumberPrivacySettingsState(
        phoneNumberSharing = true,
        discoverableByPhoneNumber = true
      )
    )
  }
}

@Preview(name = "Light Theme", group = "Screen", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark Theme", group = "Screen", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ScreenPreviewNotSharingDiscoverable() {
  SignalTheme {
    Screen(
      state = PhoneNumberPrivacySettingsState(
        phoneNumberSharing = false,
        discoverableByPhoneNumber = true
      )
    )
  }
}

@Preview(name = "Light Theme", group = "Screen", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark Theme", group = "Screen", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ScreenPreviewNotSharingNotDiscoverable() {
  SignalTheme {
    Screen(
      state = PhoneNumberPrivacySettingsState(
        phoneNumberSharing = false,
        discoverableByPhoneNumber = false
      )
    )
  }
}
