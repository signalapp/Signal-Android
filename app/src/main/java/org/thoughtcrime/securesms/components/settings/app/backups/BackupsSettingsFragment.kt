/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.backups

import android.os.Bundle
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalPreview
import org.signal.core.ui.compose.Texts
import org.signal.core.util.money.FiatMoney
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.backup.DeletionState
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.backup.v2.ui.subscription.MessageBackupsType
import org.thoughtcrime.securesms.components.compose.TextWithBetaLabel
import org.thoughtcrime.securesms.components.settings.app.subscription.MessageBackupsCheckoutLauncher.createBackupsCheckoutLauncher
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.payments.FiatMoneyUtil
import org.thoughtcrime.securesms.util.DateUtils
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import java.math.BigDecimal
import java.util.Currency
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import org.signal.core.ui.R as CoreUiR

/**
 * Top-level backups settings screen.
 */
class BackupsSettingsFragment : ComposeFragment() {

  private lateinit var checkoutLauncher: ActivityResultLauncher<MessageBackupTier?>

  private val viewModel: BackupsSettingsViewModel by viewModels()

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    checkoutLauncher = createBackupsCheckoutLauncher {
      findNavController().safeNavigate(R.id.action_backupsSettingsFragment_to_remoteBackupsSettingsFragment)
    }
  }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    BackupsSettingsContent(
      backupsSettingsState = state,
      onNavigationClick = { requireActivity().onNavigateUp() },
      onBackupsRowClick = {
        when (state.backupState) {
          BackupState.Error, BackupState.NotAvailable -> Unit

          BackupState.None -> {
            checkoutLauncher.launch(null)
          }

          else -> {
            findNavController().safeNavigate(R.id.action_backupsSettingsFragment_to_remoteBackupsSettingsFragment)
          }
        }
      },
      onOnDeviceBackupsRowClick = { findNavController().safeNavigate(R.id.action_backupsSettingsFragment_to_backupsPreferenceFragment) },
      onBackupTierInternalOverrideChanged = { viewModel.onBackupTierInternalOverrideChanged(it) }
    )
  }
}

@Composable
private fun BackupsSettingsContent(
  backupsSettingsState: BackupsSettingsState,
  onNavigationClick: () -> Unit = {},
  onBackupsRowClick: () -> Unit = {},
  onOnDeviceBackupsRowClick: () -> Unit = {},
  onBackupTierInternalOverrideChanged: (MessageBackupTier?) -> Unit = {}
) {
  Scaffolds.Settings(
    title = stringResource(R.string.preferences_chats__backups),
    navigationIcon = ImageVector.vectorResource(R.drawable.symbol_arrow_start_24),
    onNavigationClick = onNavigationClick
  ) { paddingValues ->
    LazyColumn(
      modifier = Modifier.padding(paddingValues)
    ) {
      if (backupsSettingsState.showBackupTierInternalOverride) {
        item {
          Column(modifier = Modifier.padding(horizontal = dimensionResource(id = org.signal.core.ui.R.dimen.gutter))) {
            Text(
              text = "ALPHA ONLY",
              style = MaterialTheme.typography.titleMedium
            )
            Text(
              text = "Use this to override the subscription state to one of your choosing.",
              style = MaterialTheme.typography.bodyMedium
            )
            InternalBackupOverrideRow(backupsSettingsState, onBackupTierInternalOverrideChanged)
          }
          Dividers.Default()
        }
      }

      item {
        Text(
          text = stringResource(R.string.RemoteBackupsSettingsFragment__back_up_your_message_history),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          style = MaterialTheme.typography.bodyMedium,
          modifier = Modifier.padding(horizontal = dimensionResource(CoreUiR.dimen.gutter), vertical = 16.dp)
        )
      }

      item {
        when (backupsSettingsState.backupState) {
          is BackupState.LocalStore -> {
            LocalStoreBackupRow(
              backupState = backupsSettingsState.backupState,
              lastBackupAt = backupsSettingsState.lastBackupAt,
              onBackupsRowClick = onBackupsRowClick
            )

            OtherWaysToBackUpHeading()
          }

          is BackupState.Inactive -> {
            InactiveBackupsRow(
              onBackupsRowClick = onBackupsRowClick
            )

            OtherWaysToBackUpHeading()
          }

          is BackupState.ActiveFree, is BackupState.ActivePaid, is BackupState.Canceled -> {
            ActiveBackupsRow(
              backupState = backupsSettingsState.backupState,
              onBackupsRowClick = onBackupsRowClick,
              lastBackupAt = backupsSettingsState.lastBackupAt
            )

            OtherWaysToBackUpHeading()
          }

          BackupState.None -> {
            NeverEnabledBackupsRow(
              onBackupsRowClick = onBackupsRowClick
            )

            OtherWaysToBackUpHeading()
          }

          BackupState.Error -> {
            WaitingForNetworkRow(
              onBackupsRowClick = onBackupsRowClick
            )

            OtherWaysToBackUpHeading()
          }

          BackupState.NotAvailable -> Unit

          BackupState.NotFound -> {
            NotFoundBackupRow(
              onBackupsRowClick = onBackupsRowClick
            )

            OtherWaysToBackUpHeading()
          }

          is BackupState.Pending -> {
            PendingBackupRow(
              onBackupsRowClick = onBackupsRowClick
            )

            OtherWaysToBackUpHeading()
          }

          is BackupState.SubscriptionMismatchMissingGooglePlay -> {
            ActiveBackupsRow(
              backupState = backupsSettingsState.backupState,
              lastBackupAt = backupsSettingsState.lastBackupAt
            )

            OtherWaysToBackUpHeading()
          }
        }
      }

      item {
        Rows.TextRow(
          text = stringResource(R.string.RemoteBackupsSettingsFragment__on_device_backups),
          label = stringResource(R.string.RemoteBackupsSettingsFragment__save_your_backups_to),
          onClick = onOnDeviceBackupsRowClick
        )
      }
    }
  }
}

@Composable
private fun OtherWaysToBackUpHeading() {
  Dividers.Default()

  Texts.SectionHeader(
    text = stringResource(R.string.RemoteBackupsSettingsFragment__other_ways_to_backup)
  )
}

@Composable
private fun NeverEnabledBackupsRow(
  onBackupsRowClick: () -> Unit = {}
) {
  Rows.TextRow(
    modifier = Modifier.height(IntrinsicSize.Min),
    icon = {
      Box(
        modifier = Modifier
          .fillMaxHeight()
          .padding(top = 12.dp)
      ) {
        Icon(
          painter = painterResource(R.drawable.symbol_backup_24),
          contentDescription = null
        )
      }
    },
    text = {
      Column {
        TextWithBetaLabel(
          text = stringResource(R.string.RemoteBackupsSettingsFragment__signal_backups),
          textStyle = MaterialTheme.typography.bodyLarge
        )

        Text(
          text = stringResource(R.string.BackupsSettingsFragment_automatic_backups_with_signals),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          style = MaterialTheme.typography.bodyMedium
        )

        Buttons.MediumTonal(
          onClick = onBackupsRowClick,
          modifier = Modifier.padding(top = 12.dp)
        ) {
          Text(
            text = stringResource(R.string.BackupsSettingsFragment_set_up)
          )
        }
      }
    }
  )
}

@Composable
private fun WaitingForNetworkRow(onBackupsRowClick: () -> Unit = {}) {
  Rows.TextRow(
    text = {
      Column {
        Text(text = stringResource(R.string.RemoteBackupsSettingsFragment__waiting_for_network))
        ViewSettingsButton(onBackupsRowClick)
      }
    },
    icon = {
      CircularProgressIndicator()
    }
  )
}

@Composable
private fun InactiveBackupsRow(
  onBackupsRowClick: () -> Unit = {}
) {
  Rows.TextRow(
    text = {
      Column {
        TextWithBetaLabel(
          text = stringResource(R.string.RemoteBackupsSettingsFragment__signal_backups),
          textStyle = MaterialTheme.typography.bodyLarge
        )

        Text(
          text = stringResource(R.string.RemoteBackupsSettingsFragment__off),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ViewSettingsButton(onBackupsRowClick)
      }
    },
    icon = {
      Icon(
        imageVector = ImageVector.vectorResource(R.drawable.symbol_backup_24),
        contentDescription = stringResource(R.string.preferences_chats__backups),
        tint = MaterialTheme.colorScheme.onSurface
      )
    }
  )
}

@Composable
private fun NotFoundBackupRow(
  onBackupsRowClick: () -> Unit = {}
) {
  Rows.TextRow(
    modifier = Modifier.height(IntrinsicSize.Min),
    icon = {
      Box(
        contentAlignment = Alignment.TopCenter,
        modifier = Modifier
          .fillMaxHeight()
          .padding(top = 12.dp)
      ) {
        Icon(
          painter = painterResource(R.drawable.symbol_backup_24),
          contentDescription = null
        )
      }
    },
    text = {
      Column {
        TextWithBetaLabel(
          text = stringResource(R.string.RemoteBackupsSettingsFragment__signal_backups),
          textStyle = MaterialTheme.typography.bodyLarge
        )

        Text(
          text = stringResource(R.string.BackupsSettingsFragment_subscription_not_found_on_this_device),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ViewSettingsButton(onBackupsRowClick)
      }
    }
  )
}

@Composable
private fun PendingBackupRow(
  onBackupsRowClick: () -> Unit = {}
) {
  Rows.TextRow(
    modifier = Modifier.height(IntrinsicSize.Min),
    icon = {
      Box(
        contentAlignment = Alignment.TopCenter,
        modifier = Modifier
          .fillMaxHeight()
          .padding(top = 12.dp)
      ) {
        CircularProgressIndicator(
          modifier = Modifier.size(24.dp)
        )
      }
    },
    text = {
      Column {
        TextWithBetaLabel(
          text = stringResource(R.string.RemoteBackupsSettingsFragment__signal_backups),
          textStyle = MaterialTheme.typography.bodyLarge
        )

        Text(
          text = stringResource(R.string.RemoteBackupsSettingsFragment__payment_pending),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          style = MaterialTheme.typography.bodyMedium
        )

        ViewSettingsButton(onBackupsRowClick)
      }
    }
  )
}

@Composable
private fun ViewSettingsButton(onClick: () -> Unit) {
  Buttons.MediumTonal(
    onClick = onClick,
    modifier = Modifier.padding(top = 12.dp)
  ) {
    Text(
      text = stringResource(R.string.BackupsSettingsFragment_view_settings)
    )
  }
}

@Composable
private fun LocalStoreBackupRow(
  backupState: BackupState.LocalStore,
  lastBackupAt: Duration,
  onBackupsRowClick: () -> Unit
) {
  Rows.TextRow(
    modifier = Modifier.height(IntrinsicSize.Min),
    icon = {
      Box(
        contentAlignment = Alignment.TopCenter,
        modifier = Modifier
          .fillMaxHeight()
          .padding(top = 12.dp)
      ) {
        Icon(
          painter = painterResource(R.drawable.symbol_backup_24),
          contentDescription = null
        )
      }
    },
    text = {
      Column {
        TextWithBetaLabel(
          text = stringResource(R.string.RemoteBackupsSettingsFragment__signal_backups),
          textStyle = MaterialTheme.typography.bodyLarge
        )

        val tierText = when (backupState.tier) {
          MessageBackupTier.FREE -> stringResource(R.string.RemoteBackupsSettingsFragment__your_backup_plan_is_free)
          MessageBackupTier.PAID -> stringResource(R.string.MessageBackupsTypeSelectionScreen__text_plus_all_your_media)
        }

        Text(
          text = tierText,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          style = MaterialTheme.typography.bodyMedium
        )

        LastBackedUpText(lastBackupAt)
        ViewSettingsButton(onBackupsRowClick)
      }
    }
  )
}

@Composable
private fun ActiveBackupsRow(
  backupState: BackupState.WithTypeAndRenewalTime,
  lastBackupAt: Duration,
  onBackupsRowClick: () -> Unit = {}
) {
  Rows.TextRow(
    modifier = Modifier.height(IntrinsicSize.Min),
    icon = {
      Box(
        contentAlignment = Alignment.TopCenter,
        modifier = Modifier
          .fillMaxHeight()
          .padding(top = 12.dp)
      ) {
        Icon(
          painter = painterResource(R.drawable.symbol_backup_24),
          contentDescription = null
        )
      }
    },
    text = {
      Column {
        TextWithBetaLabel(
          text = stringResource(R.string.RemoteBackupsSettingsFragment__signal_backups),
          textStyle = MaterialTheme.typography.bodyLarge
        )

        when (val type = backupState.messageBackupsType) {
          is MessageBackupsType.Paid -> {
            val body = if (backupState is BackupState.Canceled) {
              stringResource(R.string.BackupsSettingsFragment__subscription_canceled)
            } else {
              stringResource(
                R.string.BackupsSettingsFragment_s_month_renews_s,
                FiatMoneyUtil.format(LocalContext.current.resources, type.pricePerMonth),
                DateUtils.formatDateWithYear(Locale.getDefault(), backupState.renewalTime.inWholeMilliseconds)
              )
            }

            val color = if (backupState is BackupState.Canceled) {
              MaterialTheme.colorScheme.error
            } else {
              MaterialTheme.colorScheme.onSurfaceVariant
            }

            Text(
              text = body,
              color = color,
              style = MaterialTheme.typography.bodyMedium
            )
          }

          is MessageBackupsType.Free -> {
            Text(
              text = stringResource(
                R.string.RemoteBackupsSettingsFragment__your_backup_plan_is_free
              ),
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              style = MaterialTheme.typography.bodyMedium
            )
          }
        }

        LastBackedUpText(lastBackupAt)

        ViewSettingsButton(onBackupsRowClick)
      }
    }
  )
}

@Composable
private fun LastBackedUpText(lastBackupAt: Duration) {
  val lastBackupString = if (lastBackupAt.inWholeMilliseconds > 0) {
    DateUtils.getDatelessRelativeTimeSpanFormattedDate(
      LocalContext.current,
      Locale.getDefault(),
      lastBackupAt.inWholeMilliseconds
    ).value
  } else {
    stringResource(R.string.RemoteBackupsSettingsFragment__never)
  }

  Text(
    text = stringResource(
      R.string.BackupsSettingsFragment_last_backup_s,
      lastBackupString
    ),
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    style = MaterialTheme.typography.bodyMedium
  )
}

@Composable
private fun InternalBackupOverrideRow(
  backupsSettingsState: BackupsSettingsState,
  onBackupTierInternalOverrideChanged: (MessageBackupTier?) -> Unit = {}
) {
  val options = remember {
    mapOf(
      "Unset" to null,
      "Free" to MessageBackupTier.FREE,
      "Paid" to MessageBackupTier.PAID
    )
  }

  val deletionState by SignalStore.backup.deletionStateFlow.collectAsStateWithLifecycle(SignalStore.backup.deletionState)

  Row(verticalAlignment = Alignment.CenterVertically) {
    options.forEach { option ->
      RadioButton(
        enabled = deletionState == DeletionState.NONE || deletionState == DeletionState.COMPLETE,
        selected = option.value == backupsSettingsState.backupTierInternalOverride,
        onClick = { onBackupTierInternalOverrideChanged(option.value) }
      )
      Text(option.key)
    }
  }
}

@SignalPreview
@Composable
private fun BackupsSettingsContentPreview() {
  Previews.Preview {
    BackupsSettingsContent(
      backupsSettingsState = BackupsSettingsState(
        backupState = BackupState.ActivePaid(
          messageBackupsType = MessageBackupsType.Paid(
            pricePerMonth = FiatMoney(BigDecimal.valueOf(4), Currency.getInstance("CAD")),
            storageAllowanceBytes = 1_000_000,
            mediaTtl = 30.days
          ),
          renewalTime = 0.seconds,
          price = FiatMoney(BigDecimal.valueOf(4), Currency.getInstance("CAD"))
        ),
        lastBackupAt = 0.seconds
      )
    )
  }
}

@SignalPreview
@Composable
private fun BackupsSettingsContentNotAvailablePreview() {
  Previews.Preview {
    BackupsSettingsContent(
      backupsSettingsState = BackupsSettingsState(
        backupState = BackupState.NotAvailable,
        lastBackupAt = 0.seconds
      )
    )
  }
}

@SignalPreview
@Composable
private fun BackupsSettingsContentBackupTierInternalOverridePreview() {
  Previews.Preview {
    BackupsSettingsContent(
      backupsSettingsState = BackupsSettingsState(
        backupState = BackupState.None,
        showBackupTierInternalOverride = true,
        backupTierInternalOverride = null,
        lastBackupAt = 0.seconds
      )
    )
  }
}

@SignalPreview
@Composable
private fun WaitingForNetworkRowPreview() {
  Previews.Preview {
    WaitingForNetworkRow()
  }
}

@SignalPreview
@Composable
private fun InactiveBackupsRowPreview() {
  Previews.Preview {
    InactiveBackupsRow()
  }
}

@SignalPreview
@Composable
private fun NotFoundBackupRowPreview() {
  Previews.Preview {
    NotFoundBackupRow()
  }
}

@SignalPreview
@Composable
private fun PendingBackupRowPreview() {
  Previews.Preview {
    PendingBackupRow()
  }
}

@SignalPreview
@Composable
private fun ActivePaidBackupsRowPreview() {
  Previews.Preview {
    ActiveBackupsRow(
      backupState = BackupState.ActivePaid(
        messageBackupsType = MessageBackupsType.Paid(
          pricePerMonth = FiatMoney(BigDecimal.valueOf(4), Currency.getInstance("CAD")),
          storageAllowanceBytes = 1_000_000,
          mediaTtl = 30.days
        ),
        renewalTime = 0.seconds,
        price = FiatMoney(BigDecimal.valueOf(4), Currency.getInstance("CAD"))
      ),
      lastBackupAt = 0.seconds
    )
  }
}

@SignalPreview
@Composable
private fun ActiveFreeBackupsRowPreview() {
  Previews.Preview {
    ActiveBackupsRow(
      backupState = BackupState.ActiveFree(
        messageBackupsType = MessageBackupsType.Free(
          mediaRetentionDays = 30
        ),
        renewalTime = 0.seconds
      ),
      lastBackupAt = 0.seconds
    )
  }
}

@SignalPreview
@Composable
private fun NeverEnabledBackupsRowPreview() {
  Previews.Preview {
    NeverEnabledBackupsRow()
  }
}
