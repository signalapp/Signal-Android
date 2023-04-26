package org.thoughtcrime.securesms.components.settings.app.privacy.pnp

import android.os.Bundle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
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
import androidx.navigation.fragment.findNavController
import org.signal.core.ui.Dividers
import org.signal.core.ui.Rows
import org.signal.core.ui.Scaffolds
import org.signal.core.ui.Texts
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.compose.StatusBarColorNestedScrollConnection
import org.thoughtcrime.securesms.keyvalue.PhoneNumberPrivacyValues.PhoneNumberListingMode
import org.thoughtcrime.securesms.keyvalue.PhoneNumberPrivacyValues.PhoneNumberSharingMode

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

    Scaffolds.Settings(
      title = stringResource(id = R.string.preferences_app_protection__phone_number),
      onNavigationClick = onNavigationClick,
      navigationIconPainter = painterResource(id = R.drawable.ic_arrow_left_24),
      navigationContentDescription = stringResource(id = R.string.Material3SearchToolbar__close),
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
              selected = state.seeMyPhoneNumber == PhoneNumberSharingMode.EVERYONE,
              text = stringResource(id = R.string.PhoneNumberPrivacy_everyone),
              modifier = Modifier.clickable(onClick = viewModel::setEveryoneCanSeeMyNumber)
            )
          }

          item {
            Rows.RadioRow(
              selected = state.seeMyPhoneNumber == PhoneNumberSharingMode.NOBODY,
              text = stringResource(id = R.string.PhoneNumberPrivacy_nobody),
              modifier = Modifier.clickable(onClick = viewModel::setNobodyCanSeeMyNumber)
            )
          }

          item {
            Text(
              text = stringResource(
                id = when (state.seeMyPhoneNumber) {
                  PhoneNumberSharingMode.EVERYONE -> R.string.PhoneNumberPrivacySettingsFragment__your_phone_number
                  PhoneNumberSharingMode.NOBODY -> R.string.PhoneNumberPrivacySettingsFragment__nobody_will_see
                  else -> error("Unexpected state $state")
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
              selected = state.findMeByPhoneNumber == PhoneNumberListingMode.LISTED,
              text = stringResource(id = R.string.PhoneNumberPrivacy_everyone),
              modifier = Modifier.clickable(onClick = viewModel::setEveryoneCanFindMeByMyNumber)
            )
          }

          if (state.seeMyPhoneNumber == PhoneNumberSharingMode.NOBODY) {
            item {
              Rows.RadioRow(
                selected = state.findMeByPhoneNumber == PhoneNumberListingMode.UNLISTED,
                text = stringResource(id = R.string.PhoneNumberPrivacy_nobody),
                modifier = Modifier.clickable(onClick = viewModel::setNobodyCanFindMeByMyNumber)
              )
            }
          }

          item {
            Text(
              text = stringResource(
                id = when (state.findMeByPhoneNumber) {
                  PhoneNumberListingMode.UNLISTED -> R.string.WhoCanSeeMyPhoneNumberFragment__nobody_on_signal
                  PhoneNumberListingMode.LISTED -> R.string.WhoCanSeeMyPhoneNumberFragment__anyone_who_has
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
