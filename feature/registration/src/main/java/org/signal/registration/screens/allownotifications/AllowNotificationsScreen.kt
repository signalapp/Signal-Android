/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.allownotifications

import android.Manifest
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement.Absolute.spacedBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.Previews
import org.signal.registration.R
import org.signal.registration.screens.OnePaneRegistrationScaffold
import org.signal.registration.screens.RegistrationScaffold
import org.signal.registration.screens.TwoPaneRegistrationScaffold
import org.signal.registration.screens.util.MockPermissionsState
import org.signal.registration.test.TestTags

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun AllowNotificationsScreen(
  permissionState: PermissionState,
  onProceed: () -> Unit,
  modifier: Modifier = Modifier
) {
  val layoutParams = RegistrationScaffold.rememberLayoutParams()

  Surface(modifier = modifier.testTag(TestTags.ALLOW_NOTIFICATIONS_SCREEN)) {
    when (layoutParams) {
      is RegistrationScaffold.Params.OnePane -> OnePane(layoutParams, permissionState, onProceed)
      is RegistrationScaffold.Params.TwoPane -> TwoPane(layoutParams, permissionState, onProceed)
    }
  }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun OnePane(params: RegistrationScaffold.Params.OnePane, permissionState: PermissionState, onProceed: () -> Unit) {
  OnePaneRegistrationScaffold(
    params = params,
    footer = {
      FooterContent(
        permissionState = permissionState,
        onProceed = onProceed
      )
    }
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier.padding(it)
    ) {
      FirstPaneContent()
      SecondPaneContent()
    }
  }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun TwoPane(params: RegistrationScaffold.Params.TwoPane, permissionState: PermissionState, onProceed: () -> Unit) {
  TwoPaneRegistrationScaffold(
    params = params,
    firstPane = {
      FirstPaneContent(
        modifier = Modifier
          .padding(it)
          .weight(1f)
      )
    },
    secondPane = {
      SecondPaneContent(
        modifier = Modifier
          .padding(it)
          .weight(1f)
      )
    },
    footer = {
      FooterContent(
        permissionState = permissionState,
        onProceed = onProceed
      )
    }
  )
}

@Composable
private fun FirstPaneContent(
  modifier: Modifier = Modifier
) {
  Column(modifier = modifier) {
    Text(
      text = stringResource(R.string.AllowNotificationsScreen__allow_notifications),
      style = MaterialTheme.typography.headlineLarge,
      modifier = Modifier.padding(bottom = 12.dp)
    )

    Text(
      text = stringResource(R.string.AllowNotificationsScreen__signal_would_like_to_request_the_notification_permission),
      style = MaterialTheme.typography.titleMedium
    )
  }
}

@Composable
private fun SecondPaneContent(
  modifier: Modifier = Modifier
) {
  // TODO [regv5] Final image asset
  Image(
    painter = painterResource(R.drawable.welcome),
    contentDescription = null,
    modifier = modifier
  )
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun FooterContent(
  permissionState: PermissionState,
  onProceed: () -> Unit
) {
  Row(
    horizontalArrangement = spacedBy(56.dp),
    modifier = Modifier.padding(56.dp)
  ) {
    Spacer(modifier = Modifier.weight(1f))

    TextButton(
      onClick = onProceed,
      modifier = Modifier.testTag(TestTags.ALLOW_NOTIFICATIONS_NOT_NOW_BUTTON)
    ) {
      Text(text = stringResource(R.string.AllowNotificationsScreen__not_now))
    }

    Buttons.LargeTonal(
      onClick = {
        if (permissionState.status.isGranted) {
          onProceed()
        } else {
          permissionState.launchPermissionRequest()
        }
      },
      modifier = Modifier.testTag(TestTags.ALLOW_NOTIFICATIONS_NEXT_BUTTON)
    ) {
      Text(text = stringResource(R.string.AllowNotificationsScreen__next))
    }
  }
}

@OptIn(ExperimentalPermissionsApi::class)
@AllDevicePreviews
@Composable
private fun AllowNotificationsScreenPreview() {
  Previews.Preview {
    AllowNotificationsScreen(
      permissionState = MockPermissionsState(permission = Manifest.permission.POST_NOTIFICATIONS),
      onProceed = {}
    )
  }
}
