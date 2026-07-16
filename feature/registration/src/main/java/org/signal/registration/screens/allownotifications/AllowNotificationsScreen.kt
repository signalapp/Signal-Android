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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import org.signal.registration.screens.attachDebugLogHelper
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
  val scrollState = rememberScrollState()

  OnePaneRegistrationScaffold(
    params = params,
    content = { paddingValues ->
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
          .verticalScroll(scrollState)
          .padding(paddingValues)
      ) {
        FirstPaneContent()
        Spacer(modifier = Modifier.height(16.dp))
        SecondPaneContent()
      }
    },
    footer = {
      FooterContent(
        params = params,
        permissionState = permissionState,
        isElevated = scrollState.canScrollForward,
        onProceed = onProceed
      )
    }
  )
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun TwoPane(params: RegistrationScaffold.Params.TwoPane, permissionState: PermissionState, onProceed: () -> Unit) {
  val firstPaneScrollState = rememberScrollState()

  TwoPaneRegistrationScaffold(
    params = params,
    firstPane = { paddingValues ->
      FirstPaneContent(
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight()
          .verticalScroll(firstPaneScrollState)
          .padding(paddingValues)
      )
    },
    secondPane = { paddingValues ->
      SecondPaneContent(
        modifier = Modifier
          .weight(1f)
          .padding(paddingValues)
      )
    },
    footer = {
      FooterContent(
        params = params,
        permissionState = permissionState,
        isElevated = firstPaneScrollState.canScrollForward,
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
      style = MaterialTheme.typography.headlineMedium,
      modifier = Modifier
        .fillMaxWidth()
        .attachDebugLogHelper()
    )

    Text(
      text = stringResource(R.string.AllowNotificationsScreen__signal_would_like_to_request_the_notification_permission),
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(top = 16.dp)
    )
  }
}

@Composable
private fun SecondPaneContent(
  modifier: Modifier = Modifier
) {
  Image(
    painter = painterResource(R.drawable.device_notifications),
    contentDescription = null,
    modifier = modifier
  )
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun FooterContent(
  params: RegistrationScaffold.Params,
  permissionState: PermissionState,
  isElevated: Boolean,
  onProceed: () -> Unit
) {
  RegistrationScaffold.FooterSurface(
    isElevated = isElevated
  ) {
    Row(
      horizontalArrangement = spacedBy(56.dp),
      modifier = Modifier.padding(params.footerPadding)
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
