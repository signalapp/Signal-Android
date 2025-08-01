/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registrationv3.ui.permissions

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.SignalPreview
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.registrationv3.ui.shared.RegistrationScreen

/**
 * Layout that explains permissions rationale to the user.
 */
@Composable
fun GrantPermissionsScreen(
  deviceBuildVersion: Int,
  isBackupSelectionRequired: Boolean,
  onNextClicked: () -> Unit = {},
  onNotNowClicked: () -> Unit = {}
) {
  RegistrationScreen(
    title = stringResource(id = R.string.GrantPermissionsFragment__allow_permissions),
    subtitle = stringResource(id = R.string.GrantPermissionsFragment__to_help_you_message_people_you_know),
    bottomContent = {
      Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
      ) {
        TextButton(
          modifier = Modifier.weight(weight = 1f, fill = false),
          onClick = onNotNowClicked
        ) {
          Text(
            text = stringResource(id = R.string.GrantPermissionsFragment__not_now)
          )
        }

        Spacer(modifier = Modifier.size(24.dp))

        Buttons.LargeTonal(
          onClick = onNextClicked
        ) {
          Text(
            text = stringResource(id = R.string.GrantPermissionsFragment__next)
          )
        }
      }
    }
  ) {
    if (deviceBuildVersion >= 33) {
      PermissionRow(
        imageVector = ImageVector.vectorResource(id = R.drawable.permission_notification),
        title = stringResource(id = R.string.GrantPermissionsFragment__notifications),
        subtitle = stringResource(id = R.string.GrantPermissionsFragment__get_notified_when)
      )
    }

    PermissionRow(
      imageVector = ImageVector.vectorResource(id = R.drawable.permission_contact),
      title = stringResource(id = R.string.GrantPermissionsFragment__contacts),
      subtitle = stringResource(id = R.string.GrantPermissionsFragment__find_people_you_know)
    )

    if (deviceBuildVersion < 29 || !isBackupSelectionRequired) {
      PermissionRow(
        imageVector = ImageVector.vectorResource(id = R.drawable.permission_file),
        title = stringResource(id = R.string.GrantPermissionsFragment__storage),
        subtitle = stringResource(id = R.string.GrantPermissionsFragment__send_photos_videos_and_files)
      )
    }

    PermissionRow(
      imageVector = ImageVector.vectorResource(id = R.drawable.permission_phone),
      title = stringResource(id = R.string.GrantPermissionsFragment__phone_calls),
      subtitle = stringResource(id = R.string.GrantPermissionsFragment__make_registering_easier)
    )
  }
}

@SignalPreview
@Composable
fun GrantPermissionsScreenPreview() {
  Previews.Preview {
    GrantPermissionsScreen(
      deviceBuildVersion = 33,
      isBackupSelectionRequired = true
    )
  }
}

@Composable
fun PermissionRow(
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

@SignalPreview
@Composable
fun PermissionRowPreview() {
  Previews.Preview {
    PermissionRow(
      imageVector = ImageVector.vectorResource(id = R.drawable.permission_notification),
      title = stringResource(id = R.string.GrantPermissionsFragment__notifications),
      subtitle = stringResource(id = R.string.GrantPermissionsFragment__get_notified_when)
    )
  }
}
