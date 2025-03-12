/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registrationv3.ui.restore

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
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
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.registrationv3.ui.shared.RegistrationScreen
import org.thoughtcrime.securesms.util.DateUtils
import org.thoughtcrime.securesms.util.PlayStoreUtil
import org.thoughtcrime.securesms.util.viewModel
import java.util.Locale

/**
 * Restore backup from remote source.
 */
class RemoteRestoreActivity : BaseActivity() {
  companion object {

    private const val KEY_ONLY_OPTION = "ONLY_OPTION"

    fun getIntent(context: Context, isOnlyOption: Boolean = false): Intent {
      return Intent(context, RemoteRestoreActivity::class.java).apply {
        putExtra(KEY_ONLY_OPTION, isOnlyOption)
      }
    }
  }

  private val viewModel: RemoteRestoreViewModel by viewModel {
    RemoteRestoreViewModel(intent.getBooleanExtra(KEY_ONLY_OPTION, false))
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    lifecycleScope.launch {
      val restored = viewModel
        .state
        .map { it.importState }
        .filterIsInstance<RemoteRestoreViewModel.ImportState.Restored>()
        .firstOrNull()

      if (restored != null) {
        startActivity(MainActivity.clearTop(this@RemoteRestoreActivity))
        finish()
      }
    }

    setContent {
      val state: RemoteRestoreViewModel.ScreenState by viewModel.state.collectAsStateWithLifecycle()

      SignalTheme {
        Surface {
          RestoreFromBackupContent(
            state = state,
            onRestoreBackupClick = { viewModel.restore() },
            onCancelClick = {
              lifecycleScope.launch {
                if (state.isRemoteRestoreOnlyOption) {
                  viewModel.skipRestore()
                  viewModel.performStorageServiceAccountRestoreIfNeeded()
                  startActivity(MainActivity.clearTop(this@RemoteRestoreActivity))
                }

                finish()
              }
            },
            onErrorDialogDismiss = { viewModel.clearError() },
            onUpdateSignal = {
              PlayStoreUtil.openPlayStoreOrOurApkDownloadPage(this)
            }
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
}

@Composable
private fun RestoreFromBackupContent(
  state: RemoteRestoreViewModel.ScreenState,
  onRestoreBackupClick: () -> Unit = {},
  onCancelClick: () -> Unit = {},
  onErrorDialogDismiss: () -> Unit = {},
  onUpdateSignal: () -> Unit = {}
) {
  when (state.loadState) {
    RemoteRestoreViewModel.ScreenState.LoadState.LOADING -> {
      Dialogs.IndeterminateProgressDialog(
        message = stringResource(R.string.RemoteRestoreActivity__fetching_backup_details)
      )
    }

    RemoteRestoreViewModel.ScreenState.LoadState.LOADED -> {
      BackupAvailableContent(
        state = state,
        onRestoreBackupClick = onRestoreBackupClick,
        onCancelClick = onCancelClick,
        onErrorDialogDismiss = onErrorDialogDismiss,
        onUpdateSignal = onUpdateSignal
      )
    }

    RemoteRestoreViewModel.ScreenState.LoadState.NOT_FOUND -> {
      BackupNotFoundDialog(onDismiss = onCancelClick)
    }

    RemoteRestoreViewModel.ScreenState.LoadState.FAILURE -> {
      RestoreFailedDialog(onDismiss = onCancelClick)
    }

    RemoteRestoreViewModel.ScreenState.LoadState.STORAGE_SERVICE_RESTORE -> {
      Dialogs.IndeterminateProgressDialog()
    }
  }
}

@Composable
private fun BackupAvailableContent(
  state: RemoteRestoreViewModel.ScreenState,
  onRestoreBackupClick: () -> Unit,
  onCancelClick: () -> Unit,
  onErrorDialogDismiss: () -> Unit,
  onUpdateSignal: () -> Unit
) {
  val subtitle = if (state.backupSize.bytes > 0) {
    stringResource(
      id = R.string.RemoteRestoreActivity__backup_created_at_with_size,
      DateUtils.formatDateWithoutDayOfWeek(Locale.getDefault(), state.backupTime),
      DateUtils.getOnlyTimeString(LocalContext.current, state.backupTime),
      state.backupSize.toUnitString()
    )
  } else {
    stringResource(
      id = R.string.RemoteRestoreActivity__backup_created_at,
      DateUtils.formatDateWithoutDayOfWeek(Locale.getDefault(), state.backupTime),
      DateUtils.getOnlyTimeString(LocalContext.current, state.backupTime)
    )
  }

  RegistrationScreen(
    title = stringResource(id = R.string.RemoteRestoreActivity__restore_from_backup),
    subtitle = subtitle,
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
          Text(text = stringResource(id = if (state.isRemoteRestoreOnlyOption) R.string.RemoteRestoreActivity__skip_restore else android.R.string.cancel))
        }
      }
    }
  ) {
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

    when (state.importState) {
      RemoteRestoreViewModel.ImportState.None -> Unit
      RemoteRestoreViewModel.ImportState.InProgress -> RestoreProgressDialog(state.restoreProgress)
      is RemoteRestoreViewModel.ImportState.Restored -> Unit
      RemoteRestoreViewModel.ImportState.Failed -> {
        if (SignalStore.backup.hasInvalidBackupVersion) {
          InvalidBackupVersionDialog(onUpdateSignal = onUpdateSignal, onDismiss = onErrorDialogDismiss)
        } else {
          RestoreFailedDialog(onDismiss = onErrorDialogDismiss)
        }
      }
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
        backupSize = 1234567.bytes,
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
            val progressBytes = restoreProgress.count.toUnitString()
            val totalBytes = restoreProgress.estimatedTotalCount.toUnitString()
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
fun BackupNotFoundDialog(
  onDismiss: () -> Unit = {}
) {
  Dialogs.SimpleAlertDialog(
    title = stringResource(R.string.EnterBackupKey_backup_not_found),
    body = stringResource(R.string.EnterBackupKey_backup_key_you_entered_is_correct_but_no_backup),
    confirm = stringResource(android.R.string.ok),
    onConfirm = onDismiss,
    onDismiss = onDismiss
  )
}

@Composable
fun RestoreFailedDialog(
  onDismiss: () -> Unit = {}
) {
  Dialogs.SimpleAlertDialog(
    title = stringResource(R.string.RemoteRestoreActivity__couldnt_transfer),
    body = stringResource(R.string.RemoteRestoreActivity__error_occurred),
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

@Composable
fun InvalidBackupVersionDialog(
  onUpdateSignal: () -> Unit = {},
  onDismiss: () -> Unit = {}
) {
  Dialogs.SimpleAlertDialog(
    title = stringResource(R.string.RemoteRestoreActivity__couldnt_restore),
    body = stringResource(R.string.RemoteRestoreActivity__update_latest),
    confirm = stringResource(R.string.RemoteRestoreActivity__update_signal),
    onConfirm = onUpdateSignal,
    dismiss = stringResource(R.string.RemoteRestoreActivity__not_now),
    onDismiss = onDismiss
  )
}

@SignalPreview
@Composable
private fun InvalidBackupVersionDialogPreview() {
  Previews.Preview {
    InvalidBackupVersionDialog()
  }
}
