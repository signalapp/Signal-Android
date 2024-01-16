package org.thoughtcrime.securesms.components.settings.app.privacy.pnp

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
import androidx.compose.ui.unit.dp
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.launch
import org.signal.core.ui.Dividers
import org.signal.core.ui.Rows
import org.signal.core.ui.Scaffolds
import org.signal.core.ui.Texts
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.compose.StatusBarColorNestedScrollConnection

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
    val onNavigationClick: () -> Unit = remember {
      { findNavController().popBackStack() }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    Scaffolds.Settings(
      title = stringResource(id = R.string.preferences_app_protection__phone_number),
      onNavigationClick = onNavigationClick,
      navigationIconPainter = painterResource(id = R.drawable.ic_arrow_left_24),
      navigationContentDescription = stringResource(id = R.string.Material3SearchToolbar__close),
      snackbarHost = {
        SnackbarHost(snackbarHostState)
      },
      modifier = Modifier.nestedScroll(statusBarNestedScrollConnection)
    ) { contentPadding ->
      Box(modifier = Modifier.padding(contentPadding)) {
        LazyColumn {
          item {
            Texts.SectionHeader(
              text = stringResource(id = R.string.PhoneNumberPrivacySettingsFragment__who_can_see_my_number)
            )
          }

          item {
            Rows.RadioRow(
              selected = state.phoneNumberSharing,
              text = stringResource(id = R.string.PhoneNumberPrivacy_everyone),
              modifier = Modifier.clickable(onClick = viewModel::setEveryoneCanSeeMyNumber)
            )
          }

          item {
            Rows.RadioRow(
              selected = !state.phoneNumberSharing,
              text = stringResource(id = R.string.PhoneNumberPrivacy_nobody),
              modifier = Modifier.clickable(onClick = viewModel::setNobodyCanSeeMyNumber)
            )
          }

          item {
            Text(
              text = stringResource(
                id = if (state.phoneNumberSharing) {
                  R.string.PhoneNumberPrivacySettingsFragment__your_phone_number
                } else {
                  R.string.PhoneNumberPrivacySettingsFragment__nobody_will_see_your
                }
              ),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(horizontal = dimensionResource(id = R.dimen.core_ui__gutter), vertical = 16.dp)
            )
          }

          item {
            Dividers.Default()
          }

          item {
            Texts.SectionHeader(text = stringResource(id = R.string.PhoneNumberPrivacySettingsFragment__who_can_find_me_by_number))
          }

          item {
            Rows.RadioRow(
              selected = state.discoverableByPhoneNumber,
              text = stringResource(id = R.string.PhoneNumberPrivacy_everyone),
              modifier = Modifier.clickable(onClick = viewModel::setEveryoneCanFindMeByMyNumber)
            )
          }

          item {
            val snackbarMessage = stringResource(id = R.string.PhoneNumberPrivacySettingsFragment__to_change_this_setting)
            val onClick: () -> Unit = remember(state.phoneNumberSharing) {
              if (!state.phoneNumberSharing) {
                { viewModel.setNobodyCanFindMeByMyNumber() }
              } else {
                {
                  lifecycleScope.launch {
                    snackbarHostState.showSnackbar(
                      message = snackbarMessage,
                      duration = SnackbarDuration.Short
                    )
                  }
                }
              }
            }

            Rows.RadioRow(
              enabled = !state.phoneNumberSharing,
              selected = !state.discoverableByPhoneNumber,
              text = stringResource(id = R.string.PhoneNumberPrivacy_nobody),
              modifier = Modifier.clickable(onClick = onClick)
            )
          }

          item {
            Text(
              text = stringResource(
                id = if (state.discoverableByPhoneNumber) {
                  R.string.PhoneNumberPrivacySettingsFragment__anyone_who_has
                } else {
                  R.string.PhoneNumberPrivacySettingsFragment__nobody_will_be_able_to_see
                }
              ),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(horizontal = dimensionResource(id = R.dimen.core_ui__gutter), vertical = 16.dp)
            )
          }
        }
      }
    }
  }
}
