/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.restore

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.signal.core.ui.Buttons
import org.signal.core.ui.Previews
import org.signal.core.ui.theme.SignalTheme
import org.thoughtcrime.securesms.BaseActivity
import org.thoughtcrime.securesms.MainActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.backup.v2.RestoreV2Event
import org.thoughtcrime.securesms.backup.v2.ui.subscription.MessageBackupsTypeFeature
import org.thoughtcrime.securesms.backup.v2.ui.subscription.MessageBackupsTypeFeatureRow
import org.thoughtcrime.securesms.backup.v2.ui.subscription.RemoteRestoreViewModel
import org.thoughtcrime.securesms.conversation.v2.registerForLifecycle
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.ProfileUploadJob
import org.thoughtcrime.securesms.profiles.AvatarHelper
import org.thoughtcrime.securesms.profiles.edit.CreateProfileActivity
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.registration.RegistrationUtil
import org.thoughtcrime.securesms.restore.transferorrestore.TransferOrRestoreMoreOptionsDialog
import org.thoughtcrime.securesms.util.DateUtils
import org.thoughtcrime.securesms.util.Util
import java.util.Locale

class RemoteRestoreActivity : BaseActivity() {
  companion object {
    fun getIntent(context: Context): Intent {
      return Intent(context, RemoteRestoreActivity::class.java)
    }
  }

  private val viewModel: RemoteRestoreViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setContent {
      val state by viewModel.state
      SignalTheme {
        Surface {
          RestoreFromBackupContent(
            features = getFeatureList(state.backupTier),
            onRestoreBackupClick = {
              viewModel.restore()
            },
            onCancelClick = {
              finish()
            },
            onMoreOptionsClick = {
              TransferOrRestoreMoreOptionsDialog.show(fragmentManager = supportFragmentManager, skipOnly = false)
            },
            state.backupTier,
            state.backupTime,
            state.backupTier != MessageBackupTier.PAID
          )
          if (state.importState == RemoteRestoreViewModel.ImportState.RESTORED) {
            SideEffect {
              RegistrationUtil.maybeMarkRegistrationComplete()
              AppDependencies.jobManager.add(ProfileUploadJob())
              startActivity(MainActivity.clearTop(this))
            }
          } else if (state.importState == RemoteRestoreViewModel.ImportState.IN_PROGRESS) {
            ProgressDialog(state.restoreProgress)
          }
        }
      }
    }
    EventBus.getDefault().registerForLifecycle(subscriber = this, lifecycleOwner = this)
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun onEvent(restoreEvent: RestoreV2Event) {
    viewModel.updateRestoreProgress(restoreEvent)
  }

  @Composable
  private fun getFeatureList(tier: MessageBackupTier?): ImmutableList<MessageBackupsTypeFeature> {
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
  fun ProgressDialog(restoreProgress: RestoreV2Event?) {
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
                progress = restoreProgress.getProgress(),
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
              val progressBytes = Util.getPrettyFileSize(restoreProgress.count)
              val totalBytes = Util.getPrettyFileSize(restoreProgress.estimatedTotalCount)
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

  @Preview
  @Composable
  private fun ProgressDialogPreview() {
    Previews.Preview {
      ProgressDialog(RestoreV2Event(RestoreV2Event.Type.PROGRESS_RESTORE, 10, 1000))
    }
  }

  @Preview
  @Composable
  private fun RestoreFromBackupContentPreview() {
    Previews.Preview {
      RestoreFromBackupContent(
        features = persistentListOf(
          MessageBackupsTypeFeature(
            iconResourceId = R.drawable.symbol_thread_compact_bold_16,
            label = "Your last 30 days of media"
          ),
          MessageBackupsTypeFeature(
            iconResourceId = R.drawable.symbol_recent_compact_bold_16,
            label = "All of your text messages"
          )
        ),
        onRestoreBackupClick = {},
        onCancelClick = {},
        onMoreOptionsClick = {},
        MessageBackupTier.PAID,
        System.currentTimeMillis(),
        true
      )
    }
  }

  @Composable
  private fun RestoreFromBackupContent(
    features: ImmutableList<MessageBackupsTypeFeature>,
    onRestoreBackupClick: () -> Unit,
    onCancelClick: () -> Unit,
    onMoreOptionsClick: () -> Unit,
    tier: MessageBackupTier?,
    lastBackupTime: Long,
    cancelable: Boolean
  ) {
    Column(
      modifier = Modifier
        .padding(horizontal = dimensionResource(id = R.dimen.core_ui__gutter))
        .padding(top = 40.dp, bottom = 24.dp)
    ) {
      Text(
        text = stringResource(id = R.string.RemoteRestoreActivity__restore_from_backup),
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.padding(bottom = 12.dp)
      )

      val yourLastBackupText = buildAnnotatedString {
        append(
          stringResource(
            id = R.string.RemoteRestoreActivity__backup_created_at,
            DateUtils.formatDateWithoutDayOfWeek(Locale.getDefault(), lastBackupTime),
            DateUtils.getOnlyTimeString(LocalContext.current, lastBackupTime)
          )

        )
        append(" ")
        if (tier != MessageBackupTier.PAID) {
          withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
            append(stringResource(id = R.string.RemoteRestoreActivity__only_media_sent_or_received))
          }
        }
      }

      Text(
        text = yourLastBackupText,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 28.dp)
      )

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

        features.forEach {
          MessageBackupsTypeFeatureRow(
            messageBackupsTypeFeature = it,
            iconTint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 6.dp)
          )
        }
      }

      Spacer(modifier = Modifier.weight(1f))

      Buttons.LargeTonal(
        onClick = onRestoreBackupClick,
        modifier = Modifier.fillMaxWidth()
      ) {
        Text(
          text = stringResource(id = R.string.RemoteRestoreActivity__restore_backup)
        )
      }

      if (cancelable) {
        TextButton(
          onClick = onCancelClick,
          modifier = Modifier.fillMaxWidth()
        ) {
          Text(
            text = stringResource(id = android.R.string.cancel)
          )
        }
      } else {
        TextButton(
          onClick = onMoreOptionsClick,
          modifier = Modifier.fillMaxWidth()
        ) {
          Text(
            text = stringResource(id = R.string.TransferOrRestoreFragment__more_options)
          )
        }
      }
    }
  }

  private fun restoreFromServer() {
    viewModel.restore()
  }

  private fun continueRegistration() {
    if (Recipient.self().profileName.isEmpty || !AvatarHelper.hasAvatar(this, Recipient.self().id)) {
      val main = MainActivity.clearTop(this)
      val profile = CreateProfileActivity.getIntentForUserProfile(this)
      profile.putExtra("next_intent", main)
      startActivity(profile)
    } else {
      RegistrationUtil.maybeMarkRegistrationComplete()
      AppDependencies.jobManager.add(ProfileUploadJob())
      startActivity(MainActivity.clearTop(this))
    }
    finish()
  }

  @Composable
  private fun StateLabel(text: String) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelSmall,
      textAlign = TextAlign.Center
    )
  }
}
