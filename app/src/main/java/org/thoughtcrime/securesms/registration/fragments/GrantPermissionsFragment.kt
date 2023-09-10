/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.fragments

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.navArgs
import org.signal.core.ui.Buttons
import org.signal.core.ui.theme.SignalTheme
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.permissions.Permissions
import org.thoughtcrime.securesms.registration.viewmodel.RegistrationViewModel
import org.thoughtcrime.securesms.util.BackupUtil

/**
 * Fragment displayed during registration which allows a user to read through
 * what permissions are granted to Signal and why, and a means to either skip
 * granting those permissions or continue to grant via system dialogs.
 */
class GrantPermissionsFragment : ComposeFragment() {

  private val args by navArgs<GrantPermissionsFragmentArgs>()
  private val viewModel by activityViewModels<RegistrationViewModel>()
  private val isSearchingForBackup = mutableStateOf(false)

  @Composable
  override fun FragmentContent() {
    val isSearchingForBackup by this.isSearchingForBackup

    GrantPermissionsScreen(
      deviceBuildVersion = Build.VERSION.SDK_INT,
      isSearchingForBackup = isSearchingForBackup,
      isBackupSelectionRequired = BackupUtil.isUserSelectionRequired(LocalContext.current),
      onNextClicked = this::onNextClicked,
      onNotNowClicked = this::onNotNowClicked
    )
  }

  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String?>, grantResults: IntArray) {
    Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults)
  }

  private fun onNextClicked() {
    when (args.welcomeAction) {
      WelcomeAction.CONTINUE -> {
        WelcomeFragment.continueClicked(
          this,
          viewModel,
          { isSearchingForBackup.value = true },
          { isSearchingForBackup.value = false },
          GrantPermissionsFragmentDirections.actionSkipRestore(),
          GrantPermissionsFragmentDirections.actionRestore()
        )
      }

      WelcomeAction.RESTORE_BACKUP -> {
        WelcomeFragment.restoreFromBackupClicked(
          this,
          viewModel,
          GrantPermissionsFragmentDirections.actionTransferOrRestore()
        )
      }
    }
  }

  private fun onNotNowClicked() {
    when (args.welcomeAction) {
      WelcomeAction.CONTINUE -> {
        WelcomeFragment.gatherInformationAndContinue(
          this,
          viewModel,
          { isSearchingForBackup.value = true },
          { isSearchingForBackup.value = false },
          GrantPermissionsFragmentDirections.actionSkipRestore(),
          GrantPermissionsFragmentDirections.actionRestore()
        )
      }

      WelcomeAction.RESTORE_BACKUP -> {
        WelcomeFragment.gatherInformationAndChooseBackup(
          this,
          viewModel,
          GrantPermissionsFragmentDirections.actionTransferOrRestore()
        )
      }
    }
  }

  /**
   * Which welcome action the user selected which prompted this
   * screen.
   */
  enum class WelcomeAction {
    CONTINUE,
    RESTORE_BACKUP
  }
}

@Preview
@Composable
fun GrantPermissionsScreenPreview() {
  SignalTheme(isDarkMode = false) {
    GrantPermissionsScreen(
      deviceBuildVersion = 33,
      isBackupSelectionRequired = true,
      isSearchingForBackup = true,
      {},
      {}
    )
  }
}

@Composable
fun GrantPermissionsScreen(
  deviceBuildVersion: Int,
  isBackupSelectionRequired: Boolean,
  isSearchingForBackup: Boolean,
  onNextClicked: () -> Unit,
  onNotNowClicked: () -> Unit
) {
  Surface {
    Column(
      modifier = Modifier
        .padding(horizontal = 24.dp)
        .padding(top = 40.dp, bottom = 24.dp)
    ) {
      LazyColumn(
        modifier = Modifier.weight(1f)
      ) {
        item {
          Text(
            text = stringResource(id = R.string.GrantPermissionsFragment__allow_permissions),
            style = MaterialTheme.typography.headlineMedium
          )
        }

        item {
          Text(
            text = stringResource(id = R.string.GrantPermissionsFragment__to_help_you_message_people_you_know),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp, bottom = 41.dp)
          )
        }

        if (deviceBuildVersion >= 33) {
          item {
            PermissionRow(
              imageVector = ImageVector.vectorResource(id = R.drawable.permission_notification),
              title = stringResource(id = R.string.GrantPermissionsFragment__notifications),
              subtitle = stringResource(id = R.string.GrantPermissionsFragment__get_notified_when)
            )
          }
        }

        item {
          PermissionRow(
            imageVector = ImageVector.vectorResource(id = R.drawable.permission_contact),
            title = stringResource(id = R.string.GrantPermissionsFragment__contacts),
            subtitle = stringResource(id = R.string.GrantPermissionsFragment__find_people_you_know)
          )
        }

        if (deviceBuildVersion < 29 || !isBackupSelectionRequired) {
          item {
            PermissionRow(
              imageVector = ImageVector.vectorResource(id = R.drawable.permission_file),
              title = stringResource(id = R.string.GrantPermissionsFragment__storage),
              subtitle = stringResource(id = R.string.GrantPermissionsFragment__send_photos_videos_and_files)
            )
          }
        }

        item {
          PermissionRow(
            imageVector = ImageVector.vectorResource(id = R.drawable.permission_phone),
            title = stringResource(id = R.string.GrantPermissionsFragment__phone_calls),
            subtitle = stringResource(id = R.string.GrantPermissionsFragment__make_registering_easier)
          )
        }
      }

      Row {
        TextButton(onClick = onNotNowClicked) {
          Text(
            text = stringResource(id = R.string.GrantPermissionsFragment__not_now)
          )
        }

        Spacer(modifier = Modifier.weight(1f))

        if (isSearchingForBackup) {
          Box {
            NextButton(
              isSearchingForBackup = true,
              onNextClicked = onNextClicked
            )

            CircularProgressIndicator(
              modifier = Modifier.align(Alignment.Center)
            )
          }
        } else {
          NextButton(
            isSearchingForBackup = false,
            onNextClicked = onNextClicked
          )
        }
      }
    }
  }
}

@Preview
@Composable
fun PermissionRowPreview() {
  PermissionRow(
    imageVector = ImageVector.vectorResource(id = R.drawable.permission_notification),
    title = stringResource(id = R.string.GrantPermissionsFragment__notifications),
    subtitle = stringResource(id = R.string.GrantPermissionsFragment__get_notified_when)
  )
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

@Composable
fun NextButton(
  isSearchingForBackup: Boolean,
  onNextClicked: () -> Unit
) {
  val alpha = if (isSearchingForBackup) {
    0f
  } else {
    1f
  }

  Buttons.LargeTonal(
    onClick = onNextClicked,
    enabled = !isSearchingForBackup,
    modifier = Modifier.alpha(alpha)
  ) {
    Text(
      text = stringResource(id = R.string.GrantPermissionsFragment__next)
    )
  }
}
