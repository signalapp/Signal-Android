/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.dataandstorage

import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewWrapper
import org.signal.appsettings.R
import org.signal.core.models.calls.CallDataMode
import org.signal.core.models.media.SentMediaQuality
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.SignalPreviewWrapper
import org.signal.core.ui.compose.Texts
import org.signal.core.util.ByteSize
import org.signal.core.util.bytes
import org.signal.core.ui.R as CoreUiR

@VisibleForTesting
object DataAndStorageSettingsTestTags {
  const val SCROLLER = "scroller"
  const val ROW_MANAGE_STORAGE = "row-manage-storage"
  const val ROW_AUTO_DOWNLOAD_PREFIX = "row-auto-download-"
  const val ROW_SENT_MEDIA_QUALITY = "row-sent-media-quality"
  const val ROW_CALL_DATA_MODE = "row-call-data-mode"
  const val ROW_STAY_CONNECTED = "row-stay-connected"
  const val ROW_USE_PROXY = "row-use-proxy"
}

/** Order the "use less data for calls" options are listed in, from least to most data saved. */
private val CALL_DATA_MODES = listOf(CallDataMode.HIGH_ALWAYS, CallDataMode.HIGH_ON_WIFI, CallDataMode.LOW_ALWAYS)

/**
 * Lets the user control automatic media downloads, sent media quality, call data usage, and network connectivity.
 */
@Composable
fun DataAndStorageSettingsScreen(
  state: DataAndStorageSettingsState,
  onEvent: (DataAndStorageSettingsEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  Scaffolds.Settings(
    title = stringResource(R.string.preferences__data_and_storage),
    onNavigationClick = { onEvent(DataAndStorageSettingsEvent.NavigateBackClicked) },
    navigationIcon = SignalIcons.ArrowStart.imageVector,
    modifier = modifier
  ) { paddingValues ->
    LazyColumn(
      modifier = Modifier
        .padding(paddingValues)
        .testTag(DataAndStorageSettingsTestTags.SCROLLER)
    ) {
      item {
        Rows.TextRow(
          text = stringResource(R.string.preferences_data_and_storage__manage_storage),
          label = state.totalStorageUseBytes?.bytes?.toUnitString() ?: stringResource(R.string.DataAndStorageSettingsFragment__calculating),
          onClick = { onEvent(DataAndStorageSettingsEvent.ManageStorageClicked) },
          modifier = Modifier.testTag(DataAndStorageSettingsTestTags.ROW_MANAGE_STORAGE)
        )
      }

      item {
        Dividers.Default()
      }

      item {
        Texts.SectionHeader(stringResource(R.string.preferences_chats__media_auto_download))
      }

      AutoDownloadNetwork.entries.forEach { network ->
        item(key = network) {
          AutoDownloadRow(
            network = network,
            selection = state.autoDownloadFor(network),
            onEvent = onEvent
          )
        }
      }

      item {
        Description(
          stringResource(
            R.string.DataAndStorageSettingsFragment__voice_messages_and_stickers_under_size_are_always_auto_downloaded,
            state.alwaysAutoDownloadedBytes.bytes.toUnitString(unitSystem = ByteSize.UnitSystem.IEC)
          )
        )
      }

      item {
        Dividers.Default()
      }

      item {
        Texts.SectionHeader(stringResource(R.string.DataAndStorageSettingsFragment__media_quality))
      }

      item {
        Rows.RadioListRow(
          text = stringResource(R.string.DataAndStorageSettingsFragment__sent_media_quality),
          labels = SentMediaQuality.entries.map { stringResource(it.labelRes) }.toTypedArray(),
          values = SentMediaQuality.entries.map { it.name }.toTypedArray(),
          selectedValue = state.sentMediaQuality.name,
          onSelected = { onEvent(DataAndStorageSettingsEvent.SentMediaQualitySelected(SentMediaQuality.valueOf(it))) },
          modifier = Modifier.testTag(DataAndStorageSettingsTestTags.ROW_SENT_MEDIA_QUALITY)
        )
      }

      item {
        Description(stringResource(R.string.DataAndStorageSettingsFragment__sending_high_quality_media_will_use_more_data))
      }

      item {
        Dividers.Default()
      }

      item {
        Texts.SectionHeader(stringResource(R.string.DataAndStorageSettingsFragment__calls))
      }

      item {
        Rows.RadioListRow(
          text = stringResource(R.string.preferences_data_and_storage__use_less_data_for_calls),
          labels = CALL_DATA_MODES.map { stringResource(it.labelRes) }.toTypedArray(),
          values = CALL_DATA_MODES.map { it.name }.toTypedArray(),
          selectedValue = state.callDataMode.name,
          onSelected = { onEvent(DataAndStorageSettingsEvent.CallDataModeSelected(CallDataMode.valueOf(it))) },
          modifier = Modifier.testTag(DataAndStorageSettingsTestTags.ROW_CALL_DATA_MODE)
        )
      }

      item {
        Description(stringResource(R.string.preference_data_and_storage__using_less_data_may_improve_calls_on_bad_networks))
      }

      item {
        Dividers.Default()
      }

      item {
        Rows.ToggleRow(
          checked = state.isStayConnectedInBackgroundChecked,
          text = stringResource(R.string.DataAndStorageSettingsFragment__stay_connected_in_background),
          enabled = state.playServicesAvailable,
          onCheckChanged = { onEvent(DataAndStorageSettingsEvent.StayConnectedInBackgroundToggled(it)) },
          modifier = Modifier.testTag(DataAndStorageSettingsTestTags.ROW_STAY_CONNECTED)
        )
      }

      item {
        Dividers.Default()
      }

      item {
        Texts.SectionHeader(stringResource(R.string.preferences_proxy))
      }

      item {
        Rows.TextRow(
          text = stringResource(R.string.preferences_use_proxy),
          label = stringResource(if (state.isProxyEnabled) CoreUiR.string.preferences_on else CoreUiR.string.preferences_off),
          onClick = { onEvent(DataAndStorageSettingsEvent.UseProxyClicked) },
          modifier = Modifier.testTag(DataAndStorageSettingsTestTags.ROW_USE_PROXY)
        )
      }
    }

    when (state.dialog) {
      DataAndStorageSettingsState.Dialog.None -> Unit
      DataAndStorageSettingsState.Dialog.ConfirmStayConnectedInBackground -> {
        Dialogs.SimpleAlertDialog(
          title = "",
          body = stringResource(R.string.DataAndStorageSettingsFragment__staying_connected_while_in_the_background_will_likely_result_in_increased_battery_usage),
          confirm = stringResource(R.string.DataAndStorageSettingsFragment__enable),
          dismiss = stringResource(android.R.string.cancel),
          onConfirm = { onEvent(DataAndStorageSettingsEvent.StayConnectedInBackgroundConfirmed) },
          onDeny = { onEvent(DataAndStorageSettingsEvent.DialogDismissed) },
          onDismissRequest = { onEvent(DataAndStorageSettingsEvent.DialogDismissed) }
        )
      }
    }
  }
}

@Composable
private fun AutoDownloadRow(
  network: AutoDownloadNetwork,
  selection: Set<AutoDownloadMediaType>,
  onEvent: (DataAndStorageSettingsEvent) -> Unit
) {
  Rows.MultiSelectRow(
    text = stringResource(network.labelRes),
    labels = AutoDownloadMediaType.entries.map { stringResource(it.labelRes) }.toTypedArray(),
    values = AutoDownloadMediaType.entries.map { it.name }.toTypedArray(),
    selection = selection.map { it.name }.toTypedArray(),
    noSelectionLabel = stringResource(CoreUiR.string.preferences__none),
    onSelectionChanged = { values ->
      onEvent(DataAndStorageSettingsEvent.AutoDownloadChanged(network, values.map { AutoDownloadMediaType.valueOf(it) }.toSet()))
    },
    modifier = Modifier.testTag(DataAndStorageSettingsTestTags.ROW_AUTO_DOWNLOAD_PREFIX + network.name)
  )
}

@Composable
private fun Description(text: String) {
  Rows.TextRow(
    text = {
      Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  )
}

private val AutoDownloadNetwork.labelRes: Int
  @StringRes get() = when (this) {
    AutoDownloadNetwork.MOBILE -> R.string.preferences_chats__when_using_mobile_data
    AutoDownloadNetwork.WIFI -> R.string.preferences_chats__when_using_wifi
    AutoDownloadNetwork.ROAMING -> R.string.preferences_chats__when_roaming
  }

private val AutoDownloadMediaType.labelRes: Int
  @StringRes get() = when (this) {
    AutoDownloadMediaType.IMAGES -> R.string.arrays__images
    AutoDownloadMediaType.AUDIO -> R.string.arrays__audio
    AutoDownloadMediaType.VIDEO -> R.string.arrays__video
    AutoDownloadMediaType.DOCUMENTS -> R.string.arrays__documents
  }

private val SentMediaQuality.labelRes: Int
  @StringRes get() = when (this) {
    SentMediaQuality.STANDARD -> R.string.SentMediaQuality__standard
    SentMediaQuality.HIGH -> R.string.SentMediaQuality__high
  }

private val CallDataMode.labelRes: Int
  @StringRes get() = when (this) {
    CallDataMode.HIGH_ALWAYS -> R.string.preferences_data_and_storage__never
    CallDataMode.HIGH_ON_WIFI -> R.string.preferences_data_and_storage__mobile_data_only
    CallDataMode.LOW_ALWAYS -> R.string.preferences_data_and_storage__wifi_and_mobile_data
  }

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun DataAndStorageSettingsScreenPreview() {
  DataAndStorageSettingsScreen(
    state = DataAndStorageSettingsState(
      totalStorageUseBytes = 1_234_567_890,
      autoDownload = mapOf(
        AutoDownloadNetwork.MOBILE to setOf(AutoDownloadMediaType.IMAGES),
        AutoDownloadNetwork.WIFI to AutoDownloadMediaType.entries.toSet()
      ),
      alwaysAutoDownloadedBytes = 102_400
    ),
    onEvent = {}
  )
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun DataAndStorageSettingsScreenCalculatingPreview() {
  DataAndStorageSettingsScreen(
    state = DataAndStorageSettingsState(playServicesAvailable = false),
    onEvent = {}
  )
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun DataAndStorageSettingsScreenConfirmPreview() {
  DataAndStorageSettingsScreen(
    state = DataAndStorageSettingsState(dialog = DataAndStorageSettingsState.Dialog.ConfirmStayConnectedInBackground),
    onEvent = {}
  )
}
