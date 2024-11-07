/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registrationv3.ui.restore

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.signal.core.ui.Buttons
import org.signal.core.ui.Dialogs
import org.signal.core.ui.Previews
import org.signal.core.ui.SignalPreview
import org.signal.core.ui.theme.SignalTheme
import org.signal.core.util.bytes
import org.thoughtcrime.securesms.BaseActivity
import org.thoughtcrime.securesms.MainActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.backup.v2.RestoreV2Event
import org.thoughtcrime.securesms.backup.v2.ui.subscription.MessageBackupsTypeFeature
import org.thoughtcrime.securesms.backup.v2.ui.subscription.MessageBackupsTypeFeatureRow
import org.thoughtcrime.securesms.conversation.v2.registerForLifecycle
import org.thoughtcrime.securesms.profiles.edit.CreateProfileActivity
import org.thoughtcrime.securesms.registrationv3.ui.shared.RegistrationScreen
import org.thoughtcrime.securesms.util.DateUtils
import java.util.Locale

/**
 * Restore backup from remote source.
 */
class RemoteRestoreActivity : BaseActivity() {
  companion object {
    fun getIntent(context: Context): Intent {
      return Intent(context, RemoteRestoreActivity::class.java)
    }
  }

  private val viewModel: RemoteRestoreViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    lifecycleScope.launch {
      val restored = viewModel
        .state
        .map { it.importState }
        .filterIsInstance<RemoteRestoreViewModel.ImportState.Restored>()
        .firstOrNull()

      if (restored != null) {
        continueRegistration(restored.missingProfileData)
      }
    }

    setContent {
      val state: RemoteRestoreViewModel.ScreenState by viewModel.state.collectAsStateWithLifecycle()

      SignalTheme {
        Surface {
          RestoreFromBackupContent(
            state = state,
            onRestoreBackupClick = { viewModel.restore() },
            onCancelClick = { finish() },
            onErrorDialogDismiss = { viewModel.clearError() }
          )
        }
      }
    }

    EventBus.getDefault().registerForLifecycle(subscriber = this, lifecycleOwner = this)
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun onEvent(restoreEvent: RestoreV2Event) {
    viewModel.updateRestoreProgress(restoreEvent)
  }

  private fun continueRegistration(missingProfileData: Boolean) {
    val main = MainActivity.clearTop(this)

    if (missingProfileData) {
      val profile = CreateProfileActivity.getIntentForUserProfile(this)
      profile.putExtra("next_intent", main)
      startActivity(profile)
    } else {
      startActivity(main)
    }

    finish()
  }
}

@Composable
private fun RestoreFromBackupContent(
  state: RemoteRestoreViewModel.ScreenState,
  onRestoreBackupClick: () -> Unit = {},
  onCancelClick: () -> Unit = {},
  onErrorDialogDismiss: () -> Unit = {}
) {
  val subtitle = buildAnnotatedString {
    append(
      stringResource(
        id = R.string.RemoteRestoreActivity__backup_created_at,
        DateUtils.formatDateWithoutDayOfWeek(Locale.getDefault(), state.backupTime),
        DateUtils.getOnlyTimeString(LocalContext.current, state.backupTime)
      )
    )
    append(" ")
    if (state.backupTier != MessageBackupTier.PAID) {
      withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
        append(stringResource(id = R.string.RemoteRestoreActivity__only_media_sent_or_received))
      }
    }
  }

  RegistrationScreen(
    title = stringResource(id = R.string.RemoteRestoreActivity__restore_from_backup),
    subtitle = if (state.isLoaded()) subtitle else null,
    bottomContent = {
      Column {
        if (state.isLoaded()) {
          Buttons.LargeTonal(
            onClick = onRestoreBackupClick,
            modifier = Modifier.fillMaxWidth()
          ) {
            Text(text = stringResource(id = R.string.RemoteRestoreActivity__restore_backup))
          }
        }

        TextButton(
          onClick = onCancelClick,
          modifier = Modifier.fillMaxWidth()
        ) {
          Text(text = stringResource(id = android.R.string.cancel))
        }
      }
    }
  ) {
    when (state.loadState) {
      RemoteRestoreViewModel.ScreenState.LoadState.LOADING -> {
        Dialogs.IndeterminateProgressDialog(
          message = stringResource(R.string.RemoteRestoreActivity__fetching_backup_details)
        )
      }

      RemoteRestoreViewModel.ScreenState.LoadState.LOADED -> {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .background(color = SignalTheme.colors.colorSurface2, shape = RoundedCornerShape(18.dp))
            .padding(horizontal = 20.dp)
            .padding(top = 20.dp, bottom = 18.dp)
        ) {
          Text(
            text = stringResource(id = R.string.RemoteRestoreActivity__your_backup_includes),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 6.dp)
          )

          getFeatures(state.backupTier).forEach {
            MessageBackupsTypeFeatureRow(
              messageBackupsTypeFeature = it,
              iconTint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.padding(start = 16.dp, top = 6.dp)
            )
          }
        }
      }

      RemoteRestoreViewModel.ScreenState.LoadState.FAILURE -> {
        RestoreFailedDialog(onDismiss = onCancelClick)
      }
    }

    when (state.importState) {
      RemoteRestoreViewModel.ImportState.None -> Unit
      RemoteRestoreViewModel.ImportState.InProgress -> RestoreProgressDialog(state.restoreProgress)
      is RemoteRestoreViewModel.ImportState.Restored -> Unit
      RemoteRestoreViewModel.ImportState.Failed -> RestoreFailedDialog(onDismiss = onErrorDialogDismiss)
    }
  }
}

@SignalPreview
@Composable
private fun RestoreFromBackupContentPreview() {
  Previews.Preview {
    RestoreFromBackupContent(
      state = RemoteRestoreViewModel.ScreenState(
        backupTier = MessageBackupTier.PAID,
        backupTime = System.currentTimeMillis(),
        importState = RemoteRestoreViewModel.ImportState.None,
        restoreProgress = null
      )
    )
  }
}

@SignalPreview
@Composable
private fun RestoreFromBackupContentLoadingPreview() {
  Previews.Preview {
    RestoreFromBackupContent(
      state = RemoteRestoreViewModel.ScreenState(
        importState = RemoteRestoreViewModel.ImportState.None,
        restoreProgress = null
      )
    )
  }
}

@Composable
private fun getFeatures(tier: MessageBackupTier?): ImmutableList<MessageBackupsTypeFeature> {
  return when (tier) {
    null -> persistentListOf()
    MessageBackupTier.PAID -> {
      persistentListOf(
        MessageBackupsTypeFeature(
          iconResourceId = R.drawable.symbol_thread_compact_bold_16,
          label = stringResource(id = R.string.RemoteRestoreActivity__all_of_your_media)
        ),
        MessageBackupsTypeFeature(
          iconResourceId = R.drawable.symbol_recent_compact_bold_16,
          label = stringResource(id = R.string.RemoteRestoreActivity__all_of_your_messages)
        )
      )
    }

    MessageBackupTier.FREE -> {
      persistentListOf(
        MessageBackupsTypeFeature(
          iconResourceId = R.drawable.symbol_thread_compact_bold_16,
          label = stringResource(id = R.string.RemoteRestoreActivity__your_last_d_days_of_media, 30)
        ),
        MessageBackupsTypeFeature(
          iconResourceId = R.drawable.symbol_recent_compact_bold_16,
          label = stringResource(id = R.string.RemoteRestoreActivity__all_of_your_messages)
        )
      )
    }
  }
}

/**
 * A dialog that *just* shows a spinner. Useful for short actions where you need to
 * let the user know that some action is completing.
 */
@Composable
private fun RestoreProgressDialog(restoreProgress: RestoreV2Event?) {
  androidx.compose.material3.AlertDialog(
    onDismissRequest = {},
    confirmButton = {},
    dismissButton = {},
    text = {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .fillMaxWidth()
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier.wrapContentSize()
        ) {
          if (restoreProgress == null) {
            CircularProgressIndicator(
              modifier = Modifier
                .padding(top = 55.dp, bottom = 16.dp)
                .width(48.dp)
                .height(48.dp)
            )
          } else {
            CircularProgressIndicator(
              progress = { restoreProgress.getProgress() },
              modifier = Modifier
                .padding(top = 55.dp, bottom = 16.dp)
                .width(48.dp)
                .height(48.dp)
            )
          }

          val progressText = when (restoreProgress?.type) {
            RestoreV2Event.Type.PROGRESS_DOWNLOAD -> stringResource(id = R.string.RemoteRestoreActivity__downloading_backup)
            RestoreV2Event.Type.PROGRESS_RESTORE -> stringResource(id = R.string.RemoteRestoreActivity__downloading_backup)
            else -> stringResource(id = R.string.RemoteRestoreActivity__restoring)
          }

          Text(
            text = progressText,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 12.dp)
          )

          if (restoreProgress != null) {
            val progressBytes = restoreProgress.count.toUnitString(maxPlaces = 2)
            val totalBytes = restoreProgress.estimatedTotalCount.toUnitString(maxPlaces = 2)
            Text(
              text = stringResource(id = R.string.RemoteRestoreActivity__s_of_s_s, progressBytes, totalBytes, "%.2f%%".format(restoreProgress.getProgress())),
              style = MaterialTheme.typography.bodySmall,
              modifier = Modifier.padding(bottom = 12.dp)
            )
          }
        }
      }
    },
    modifier = Modifier.width(212.dp)
  )
}

@SignalPreview
@Composable
private fun ProgressDialogPreview() {
  Previews.Preview {
    RestoreProgressDialog(
      RestoreV2Event(
        type = RestoreV2Event.Type.PROGRESS_RESTORE,
        count = 1234.bytes,
        estimatedTotalCount = 10240.bytes
      )
    )
  }
}

@Composable
fun RestoreFailedDialog(
  onDismiss: () -> Unit = {}
) {
  Dialogs.SimpleAlertDialog(
    title = "Restore Failed", // TODO [backups] Remote restore error placeholder copy
    body = "Unable to restore from backup. Please try again.", // TODO [backups] Placeholder copy
    confirm = stringResource(android.R.string.ok),
    onConfirm = onDismiss,
    onDismiss = onDismiss
  )
}

@SignalPreview
@Composable
private fun RestoreFailedDialogPreview() {
  Previews.Preview {
    RestoreFailedDialog()
  }
}
