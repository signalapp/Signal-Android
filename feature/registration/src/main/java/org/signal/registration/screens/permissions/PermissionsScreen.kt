/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

@file:OptIn(ExperimentalPermissionsApi::class)

package org.signal.registration.screens.permissions

import android.Manifest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.registration.screens.util.MockMultiplePermissionsState
import org.signal.registration.screens.util.MockPermissionsState
import org.signal.registration.test.TestTags

/**
 * Permissions screen for the registration flow.
 * Requests necessary runtime permissions before continuing.
 *
 * @param permissionsState The permissions state managed at the activity level.
 * @param onEvent Callback for screen events.
 * @param modifier Modifier to be applied to the root container.
 */
@Composable
fun PermissionsScreen(
  permissionsState: MultiplePermissionsState,
  onProceed: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    Text(
      text = "Permissions",
      style = MaterialTheme.typography.headlineLarge,
      textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(16.dp))

    Text(
      text = "Signal needs the following permissions to provide the best experience:",
      style = MaterialTheme.typography.bodyLarge,
      textAlign = TextAlign.Center,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(24.dp))

    PermissionsList(permissions = permissionsState.permissions.map { it.permission })

    Spacer(modifier = Modifier.height(48.dp))

    Button(
      onClick = {
        permissionsState.launchMultiplePermissionRequest()
        onProceed()
      },
      modifier = Modifier
        .fillMaxWidth()
        .testTag(TestTags.PERMISSIONS_NEXT_BUTTON)
    ) {
      Text("Next")
    }

    OutlinedButton(
      onClick = { onProceed() },
      modifier = Modifier
        .fillMaxWidth()
        .testTag(TestTags.PERMISSIONS_NOT_NOW_BUTTON)
    ) {
      Text("Not now")
    }
  }
}

/**
 * Displays a list of permission explanations.
 */
@Composable
private fun PermissionsList(
  permissions: List<String>,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    val permissionDescriptions = getPermissionDescriptions(permissions)
    permissionDescriptions.forEach { description ->
      PermissionItem(description = description)
    }
  }
}

/**
 * Individual permission item with description.
 */
@Composable
private fun PermissionItem(
  description: String,
  modifier: Modifier = Modifier
) {
  Text(
    text = "• $description",
    style = MaterialTheme.typography.bodyMedium,
    modifier = modifier.fillMaxWidth()
  )
}

/**
 * Converts permission names to user-friendly descriptions.
 */
private fun getPermissionDescriptions(permissions: List<String>): List<String> {
  return buildList {
    if (permissions.any { it == Manifest.permission.POST_NOTIFICATIONS }) {
      add("Notifications - Stay updated with new messages")
    }
    if (permissions.any { it == Manifest.permission.READ_CONTACTS || it == Manifest.permission.WRITE_CONTACTS }) {
      add("Contacts - Find friends who use Signal")
    }
    if (permissions.any {
        it == Manifest.permission.READ_EXTERNAL_STORAGE ||
          it == Manifest.permission.WRITE_EXTERNAL_STORAGE ||
          it == Manifest.permission.READ_MEDIA_IMAGES ||
          it == Manifest.permission.READ_MEDIA_VIDEO ||
          it == Manifest.permission.READ_MEDIA_AUDIO
      }
    ) {
      add("Photos and media - Share images and videos")
    }
    if (permissions.any { it == Manifest.permission.READ_PHONE_STATE || it == Manifest.permission.READ_PHONE_NUMBERS }) {
      add("Phone - Verify your phone number")
    }
  }
}

@DayNightPreviews
@Composable
private fun PermissionsScreenPreview() {
  Previews.Preview {
    PermissionsScreen(
      permissionsState = MockMultiplePermissionsState(
        permissions = listOf(
          Manifest.permission.POST_NOTIFICATIONS,
          Manifest.permission.READ_CONTACTS,
          Manifest.permission.WRITE_CONTACTS,
          Manifest.permission.READ_PHONE_STATE,
          Manifest.permission.READ_EXTERNAL_STORAGE,
          Manifest.permission.WRITE_EXTERNAL_STORAGE
        ).map { MockPermissionsState(it) }
      ),
      onProceed = {}
    )
  }
}
