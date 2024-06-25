/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui.restore

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.signal.core.ui.Buttons
import org.signal.core.ui.Previews
import org.signal.core.ui.theme.SignalTheme
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.backup.v2.ui.subscription.MessageBackupsTypeFeature
import org.thoughtcrime.securesms.backup.v2.ui.subscription.MessageBackupsTypeFeatureRow
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.devicetransfer.moreoptions.MoreTransferOrRestoreOptionsMode
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Fragment which facilitates restoring from a backup during
 * registration.
 */
class RestoreFromBackupFragment : ComposeFragment() {

  private val navArgs: RestoreFromBackupFragmentArgs by navArgs()

  @Composable
  override fun FragmentContent() {
    RestoreFromBackupContent(
      features = persistentListOf(),
      onRestoreBackupClick = {
        // TODO [message-backups] Restore backup.
      },
      onCancelClick = {
        findNavController()
          .popBackStack()
      },
      onMoreOptionsClick = {
        findNavController()
          .safeNavigate(RestoreFromBackupFragmentDirections.actionRestoreFromBacakupFragmentToMoreOptions(MoreTransferOrRestoreOptionsMode.SELECTION))
      },
      cancelable = navArgs.cancelable
    )
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
  cancelable: Boolean
) {
  Column(
    modifier = Modifier
      .padding(horizontal = dimensionResource(id = R.dimen.core_ui__gutter))
      .padding(top = 40.dp, bottom = 24.dp)
  ) {
    Text(
      text = "Restore from backup", // TODO [message-backups] Finalized copy.
      style = MaterialTheme.typography.headlineMedium,
      modifier = Modifier.padding(bottom = 12.dp)
    )

    val yourLastBackupText = buildAnnotatedString {
      append("Your last backup was made on March 5, 2024 at 9:00am.") // TODO [message-backups] Finalized copy.
      append(" ")
      withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
        append("Only media sent or received in the past 30 days is included.") // TODO [message-backups] Finalized copy.
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
        text = "Your backup includes:", // TODO [message-backups] Finalized copy.
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
        text = "Restore backup" // TODO [message-backups] Finalized copy.
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
