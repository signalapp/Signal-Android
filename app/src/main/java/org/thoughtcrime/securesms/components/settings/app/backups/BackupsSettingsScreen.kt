/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.backups

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import org.signal.appsettings.AppSettingsLazyColumn
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.SignalPreviewWrapper
import org.signal.core.ui.compose.Texts
import org.signal.core.util.money.FiatMoney
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.backup.DeletionState
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.backup.v2.ui.subscription.MessageBackupsType
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.payments.FiatMoneyUtil
import org.thoughtcrime.securesms.util.DateUtils
import java.math.BigDecimal
import java.util.Currency
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.signal.core.ui.R as CoreUiR

/**
 * Top-level backups settings, showing the state of the user's remote backups and the way into on-device backups.
 */
@Composable
fun BackupsSettingsScreen(
  state: BackupsSettingsState,
  onEvent: (BackupsSettingsEvent) -> Unit
) {
  BackupsSettingsContent(
    backupsSettingsState = state,
    onNavigationClick = { onEvent(BackupsSettingsEvent.NavigateBackClicked) },
    onBackupsRowClick = { onEvent(BackupsSettingsEvent.BackupsRowClicked) },
    onOnDeviceBackupsRowClick = { onEvent(BackupsSettingsEvent.OnDeviceBackupsRowClicked) },
    onBackupTierInternalOverrideChanged = { onEvent(BackupsSettingsEvent.BackupTierInternalOverrideChanged(it)) },
    onLearnMoreClick = { onEvent(BackupsSettingsEvent.LearnMoreClicked) }
  )
}

@Composable
private fun BackupsSettingsContent(
  backupsSettingsState: BackupsSettingsState,
  onNavigationClick: () -> Unit = {},
  onBackupsRowClick: () -> Unit = {},
  onOnDeviceBackupsRowClick: () -> Unit = {},
  onBackupTierInternalOverrideChanged: (MessageBackupTier?) -> Unit = {},
  onLearnMoreClick: () -> Unit = {}
) {
  Scaffolds.Settings(
    title = stringResource(R.string.preferences_chats__backups),
    navigationIcon = SignalIcons.ArrowStart.imageVector,
    navigationContentDescription = stringResource(CoreUiR.string.CloseButton__content_description),
    onNavigationClick = onNavigationClick,
    expandableTitle = true
  ) { paddingValues ->
    AppSettingsLazyColumn(
      scaffoldPadding = paddingValues
    ) {
      if (backupsSettingsState.showBackupTierInternalOverride) {
        item {
          Column(modifier = Modifier.padding(horizontal = dimensionResource(id = CoreUiR.dimen.gutter))) {
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
            if (backupsSettingsState.isLinkedDevice) {
              BackupsOffLinkedDeviceRow(onLearnMoreClick = onLearnMoreClick)
            } else {
              InactiveBackupsRow(
                onBackupsRowClick = onBackupsRowClick
              )
            }

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
            if (backupsSettingsState.isLinkedDevice) {
              BackupsOffLinkedDeviceRow(onLearnMoreClick = onLearnMoreClick)
            } else {
              NeverEnabledBackupsRow(
                onBackupsRowClick = onBackupsRowClick
              )
            }

            OtherWaysToBackUpHeading()
          }

          is BackupState.Error -> {
            WaitingForNetworkRow(
              onBackupsRowClick = onBackupsRowClick
            )

            OtherWaysToBackUpHeading()
          }

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
              lastBackupAt = backupsSettingsState.lastBackupAt,
              onBackupsRowClick = onBackupsRowClick
            )

            OtherWaysToBackUpHeading()
          }
        }
      }

      item {
        Rows.TextRow(
          text = stringResource(R.string.RemoteBackupsSettingsFragment__on_device_backups),
          icon = ImageVector.vectorResource(CoreUiR.drawable.symbol_device_phone_24),
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
    modifier = Modifier.wrapContentHeight(),
    icon = {
      Box(
        modifier = Modifier
          .padding(top = 12.dp)
          .align(Alignment.Top)
      ) {
        Icon(
          painter = SignalIcons.Backup.painter,
          contentDescription = null
        )
      }
    },
    text = {
      Column {
        Text(
          text = stringResource(R.string.RemoteBackupsSettingsFragment__signal_backups),
          style = MaterialTheme.typography.bodyLarge
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
private fun BackupsOffLinkedDeviceRow(
  onLearnMoreClick: () -> Unit = {}
) {
  val description = buildAnnotatedString {
    append(stringResource(R.string.BackupsSettingsFragment__automatic_backups_get_started_on_your_phone))
    append(" ")
    withLink(LinkAnnotation.Clickable(tag = "learn-more") { onLearnMoreClick() }) {
      withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
        append(stringResource(R.string.RemoteBackupsSettingsFragment__learn_more))
      }
    }
  }

  Rows.TextRow(
    modifier = Modifier.wrapContentHeight(),
    icon = {
      Box(
        modifier = Modifier
          .padding(top = 12.dp)
          .align(Alignment.Top)
      ) {
        Icon(
          painter = SignalIcons.Backup.painter,
          contentDescription = null
        )
      }
    },
    text = {
      Column {
        Text(
          text = stringResource(R.string.RemoteBackupsSettingsFragment__signal_backups),
          style = MaterialTheme.typography.bodyLarge
        )

        Text(
          text = description,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          style = MaterialTheme.typography.bodyMedium
        )
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
        Text(
          text = stringResource(R.string.RemoteBackupsSettingsFragment__signal_backups),
          style = MaterialTheme.typography.bodyLarge
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
        imageVector = SignalIcons.Backup.imageVector,
        contentDescription = stringResource(R.string.preferences_chats__backups),
        tint = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
          .padding(top = 12.dp)
          .align(Alignment.Top)
      )
    }
  )
}

@Composable
private fun NotFoundBackupRow(
  onBackupsRowClick: () -> Unit = {}
) {
  Rows.TextRow(
    modifier = Modifier.wrapContentHeight(),
    icon = {
      Box(
        modifier = Modifier
          .padding(top = 12.dp)
          .align(Alignment.Top)
      ) {
        Icon(
          painter = SignalIcons.Backup.painter,
          contentDescription = null
        )
      }
    },
    text = {
      Column {
        Text(
          text = stringResource(R.string.RemoteBackupsSettingsFragment__signal_backups),
          style = MaterialTheme.typography.bodyLarge
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
    modifier = Modifier.wrapContentHeight(),
    icon = {
      Box(
        modifier = Modifier
          .padding(top = 12.dp)
          .align(Alignment.Top)
      ) {
        CircularProgressIndicator(
          modifier = Modifier.size(24.dp)
        )
      }
    },
    text = {
      Column {
        Text(
          text = stringResource(R.string.RemoteBackupsSettingsFragment__signal_backups),
          style = MaterialTheme.typography.bodyLarge
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
    modifier = Modifier.wrapContentHeight(),
    icon = {
      Box(
        modifier = Modifier
          .padding(top = 12.dp)
          .align(Alignment.Top)
      ) {
        Icon(
          painter = SignalIcons.Backup.painter,
          contentDescription = null
        )
      }
    },
    text = {
      Column {
        Text(
          text = stringResource(R.string.RemoteBackupsSettingsFragment__signal_backups),
          style = MaterialTheme.typography.bodyLarge
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
    modifier = Modifier.wrapContentHeight(),
    icon = {
      Box(
        modifier = Modifier
          .padding(top = 12.dp)
          .align(Alignment.Top)
      ) {
        Icon(
          painter = SignalIcons.Backup.painter,
          contentDescription = null
        )
      }
    },
    text = {
      Column {
        Text(
          text = stringResource(R.string.RemoteBackupsSettingsFragment__signal_backups),
          style = MaterialTheme.typography.bodyLarge
        )

        val locale = LocalLocale.current.platformLocale
        when (val type = backupState.messageBackupsType) {
          is MessageBackupsType.Paid -> {
            val body = if (backupState is BackupState.Canceled) {
              stringResource(R.string.BackupsSettingsFragment__subscription_canceled)
            } else if (type.pricePerMonth.amount == BigDecimal.ZERO) {
              stringResource(
                R.string.BackupsSettingsFragment_renews_s,
                DateUtils.formatDateWithYear(locale, backupState.renewalTime.inWholeMilliseconds)
              )
            } else {
              stringResource(
                R.string.BackupsSettingsFragment_s_month_renews_s,
                FiatMoneyUtil.format(LocalContext.current.resources, type.pricePerMonth),
                DateUtils.formatDateWithYear(locale, backupState.renewalTime.inWholeMilliseconds)
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
  val context = LocalContext.current

  var lastBackupString by remember(lastBackupAt) { mutableStateOf(calculateLastBackupTimeString(context, lastBackupAt)) }

  LaunchedEffect(lastBackupAt) {
    while (true) {
      delay(1.minutes)
      lastBackupString = calculateLastBackupTimeString(context, lastBackupAt)
    }
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

private fun calculateLastBackupTimeString(context: Context, lastBackupAt: Duration): String {
  return if (lastBackupAt.inWholeMilliseconds > 0) {
    val relativeTime = DateUtils.getDatelessRelativeTimeSpanFormattedDate(
      context,
      Locale.getDefault(),
      lastBackupAt.inWholeMilliseconds
    )

    if (relativeTime.isRelative) {
      relativeTime.value
    } else {
      val day = DateUtils.getDayPrecisionTimeString(context, Locale.getDefault(), lastBackupAt.inWholeMilliseconds)
      val time = relativeTime.value

      context.getString(R.string.RemoteBackupsSettingsFragment__s_at_s, day, time)
    }
  } else {
    context.getString(R.string.RemoteBackupsSettingsFragment__never)
  }
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

@DayNightPreviews
@PreviewWrapper(SignalPreviewWrapper::class)
@Composable
private fun BackupsSettingsContentPreview() {
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
      lastBackupAt = 0.seconds,
      isLinkedDevice = false
    )
  )
}

@DayNightPreviews
@PreviewWrapper(SignalPreviewWrapper::class)
@Composable
private fun BackupsSettingsContentLinkedDevicePreview() {
  BackupsSettingsContent(
    backupsSettingsState = BackupsSettingsState(
      backupState = BackupState.ActivePaid(
        messageBackupsType = MessageBackupsType.Paid(
          pricePerMonth = FiatMoney(BigDecimal.valueOf(2.99), Currency.getInstance("USD")),
          storageAllowanceBytes = 1_000_000,
          mediaTtl = 30.days
        ),
        renewalTime = 0.seconds,
        price = FiatMoney(BigDecimal.valueOf(2.99), Currency.getInstance("USD"))
      ),
      lastBackupAt = 0.seconds,
      isLinkedDevice = true
    )
  )
}

@DayNightPreviews
@PreviewWrapper(SignalPreviewWrapper::class)
@Composable
private fun WaitingForNetworkRowPreview() {
  WaitingForNetworkRow()
}

@DayNightPreviews
@PreviewWrapper(SignalPreviewWrapper::class)
@Composable
private fun InactiveBackupsRowPreview() {
  InactiveBackupsRow()
}

@DayNightPreviews
@PreviewWrapper(SignalPreviewWrapper::class)
@Composable
private fun NotFoundBackupRowPreview() {
  NotFoundBackupRow()
}

@DayNightPreviews
@PreviewWrapper(SignalPreviewWrapper::class)
@Composable
private fun PendingBackupRowPreview() {
  PendingBackupRow()
}

@DayNightPreviews
@PreviewWrapper(SignalPreviewWrapper::class)
@Composable
private fun ActivePaidBackupsRowPreview() {
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

@DayNightPreviews
@PreviewWrapper(SignalPreviewWrapper::class)
@Composable
private fun ActivePaidBackupsRowNoPricePreview() {
  ActiveBackupsRow(
    backupState = BackupState.ActivePaid(
      messageBackupsType = MessageBackupsType.Paid(
        pricePerMonth = FiatMoney(BigDecimal.ZERO, Currency.getInstance("CAD")),
        storageAllowanceBytes = 1_000_000,
        mediaTtl = 30.days
      ),
      renewalTime = 0.seconds,
      price = FiatMoney(BigDecimal.valueOf(4), Currency.getInstance("CAD"))
    ),
    lastBackupAt = 0.seconds
  )
}

@DayNightPreviews
@PreviewWrapper(SignalPreviewWrapper::class)
@Composable
private fun ActiveFreeBackupsRowPreview() {
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

@DayNightPreviews
@PreviewWrapper(SignalPreviewWrapper::class)
@Composable
private fun NeverEnabledBackupsRowPreview() {
  NeverEnabledBackupsRow()
}

@DayNightPreviews
@PreviewWrapper(SignalPreviewWrapper::class)
@Composable
private fun BackupsOffLinkedDeviceRowPreview() {
  BackupsOffLinkedDeviceRow()
}
