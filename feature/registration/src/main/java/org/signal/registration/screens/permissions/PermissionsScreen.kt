/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

@file:OptIn(ExperimentalPermissionsApi::class)

package org.signal.registration.screens.permissions

import android.Manifest
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.horizontalGutters
import org.signal.registration.R
import org.signal.registration.screens.util.MockMultiplePermissionsState
import org.signal.registration.screens.util.MockPermissionsState
import org.signal.registration.test.TestTags

/**
 * Permissions screen for the registration flow.
 * Requests necessary runtime permissions before continuing.
 *
 * @param permissionsState The permissions state managed at the activity level.
 * @param onProceed Callback invoked when the user proceeds (either granting or skipping).
 * @param modifier Modifier to be applied to the root container.
 */
@Composable
fun PermissionsScreen(
  permissionsState: MultiplePermissionsState,
  onProceed: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  val permissions = permissionsState.permissions.map { it.permission }

  Surface(modifier = modifier.testTag(TestTags.PERMISSIONS_SCREEN)) {
    Column(
      verticalArrangement = Arrangement.SpaceBetween,
      modifier = Modifier
        .fillMaxWidth()
        .fillMaxHeight()
    ) {
      val scrollState = rememberScrollState()

      Column(
        modifier = Modifier
          .verticalScroll(scrollState)
          .weight(weight = 1f, fill = false)
          .padding(bottom = 16.dp)
          .horizontalGutters()
      ) {
        Spacer(Modifier.height(40.dp))

        Text(
          text = stringResource(id = R.string.GrantPermissionsFragment__allow_permissions),
          style = MaterialTheme.typography.headlineMedium,
          modifier = Modifier.fillMaxWidth()
        )

        Text(
          text = stringResource(id = R.string.GrantPermissionsFragment__to_help_you_message_people_you_know),
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(top = 16.dp)
        )

        Spacer(modifier = Modifier.height(40.dp))

        if (permissions.any { it == Manifest.permission.POST_NOTIFICATIONS }) {
          PermissionRow(
            imageVector = ImageVector.vectorResource(id = R.drawable.permission_notification),
            title = stringResource(id = R.string.GrantPermissionsFragment__notifications),
            subtitle = stringResource(id = R.string.GrantPermissionsFragment__get_notified_when)
          )
        }

        if (permissions.any { it == Manifest.permission.READ_CONTACTS || it == Manifest.permission.WRITE_CONTACTS }) {
          PermissionRow(
            imageVector = ImageVector.vectorResource(id = R.drawable.permission_contact),
            title = stringResource(id = R.string.GrantPermissionsFragment__contacts),
            subtitle = stringResource(id = R.string.GrantPermissionsFragment__find_people_you_know)
          )
        }

        if (permissions.any {
            it == Manifest.permission.READ_EXTERNAL_STORAGE ||
              it == Manifest.permission.WRITE_EXTERNAL_STORAGE ||
              it == Manifest.permission.READ_MEDIA_IMAGES ||
              it == Manifest.permission.READ_MEDIA_VIDEO ||
              it == Manifest.permission.READ_MEDIA_AUDIO
          }
        ) {
          PermissionRow(
            imageVector = ImageVector.vectorResource(id = R.drawable.permission_file),
            title = stringResource(id = R.string.GrantPermissionsFragment__storage),
            subtitle = stringResource(id = R.string.GrantPermissionsFragment__send_photos_videos_and_files)
          )
        }

        if (permissions.any { it == Manifest.permission.READ_PHONE_STATE || it == Manifest.permission.READ_PHONE_NUMBERS }) {
          PermissionRow(
            imageVector = ImageVector.vectorResource(id = R.drawable.permission_phone),
            title = stringResource(id = R.string.GrantPermissionsFragment__phone_calls),
            subtitle = stringResource(id = R.string.GrantPermissionsFragment__make_registering_easier)
          )
        }
      }

      Surface(
        shadowElevation = if (scrollState.canScrollForward) 8.dp else 0.dp,
        modifier = Modifier.fillMaxWidth()
      ) {
        Box(
          modifier = Modifier
            .padding(top = 8.dp, bottom = 24.dp)
            .horizontalGutters()
        ) {
          Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
          ) {
            TextButton(
              modifier = Modifier
                .weight(weight = 1f, fill = false)
                .testTag(TestTags.PERMISSIONS_NOT_NOW_BUTTON),
              onClick = onProceed
            ) {
              Text(
                text = stringResource(id = R.string.GrantPermissionsFragment__not_now)
              )
            }

            Spacer(modifier = Modifier.size(24.dp))

            Buttons.LargeTonal(
              modifier = Modifier.testTag(TestTags.PERMISSIONS_NEXT_BUTTON),
              onClick = {
                permissionsState.launchMultiplePermissionRequest()
                onProceed()
              }
            ) {
              Text(
                text = stringResource(id = R.string.GrantPermissionsFragment__next)
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun PermissionRow(
  imageVector: ImageVector,
  title: String,
  subtitle: String
) {
  Row(modifier = Modifier.padding(bottom = 32.dp)) {
    Image(
      imageVector = imageVector,
      contentDescription = null,
      modifier = Modifier.size(48.dp)
    )

    Spacer(modifier = Modifier.size(16.dp))

    Column {
      Text(
        text = title,
        style = MaterialTheme.typography.titleSmall
      )

      Text(
        text = subtitle,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }

    Spacer(modifier = Modifier.size(32.dp))
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
