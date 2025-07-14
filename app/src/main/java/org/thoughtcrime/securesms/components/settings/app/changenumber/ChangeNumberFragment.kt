/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.changenumber

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalPreview
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.util.navigation.safeNavigate

class ChangeNumberFragment : ComposeFragment() {

  @Composable
  override fun FragmentContent() {
    val navController: NavController = remember { findNavController() }
    ChangeNumberScreen(
      onNavigationIconClick = {
        navController.popBackStack()
      },
      onContinueClick = {
        navController.safeNavigate(ChangeNumberFragmentDirections.actionChangePhoneNumberFragmentToEnterPhoneNumberChangeFragment())
      }
    )
  }
}

@Composable
fun ChangeNumberScreen(
  onNavigationIconClick: () -> Unit,
  onContinueClick: () -> Unit
) {
  Scaffolds.Settings(
    title = "",
    onNavigationClick = onNavigationIconClick,
    navigationIcon = ImageVector.vectorResource(id = R.drawable.ic_arrow_left_24),
    navigationContentDescription = stringResource(id = R.string.Material3SearchToolbar__close)
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier
        .fillMaxSize()
        .padding(it)
        .padding(horizontal = 32.dp)
    ) {
      Image(
        painter = painterResource(id = R.drawable.change_number_hero_image),
        contentDescription = null,
        modifier = Modifier
          .padding(top = 20.dp)
      )

      Text(
        text = stringResource(id = R.string.AccountSettingsFragment__change_phone_number),
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 24.dp)
      )

      Text(
        text = stringResource(id = R.string.ChangeNumberFragment__use_this_to_change_your_current_phone_number_to_a_new_phone_number),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 12.dp)
      )

      Box(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f)
          .padding(bottom = 32.dp)
      ) {
        Buttons.LargePrimary(
          onClick = onContinueClick,
          modifier = Modifier.fillMaxWidth()
            .align(Alignment.BottomCenter)
        ) {
          Text(
            text = stringResource(id = R.string.ChangeNumberFragment__continue)
          )
        }
      }
    }
  }
}

@SignalPreview
@Composable
private fun MessageBackupsEducationSheetPreview() {
  Previews.Preview {
    ChangeNumberScreen(
      onNavigationIconClick = {},
      onContinueClick = {}
    )
  }
}
