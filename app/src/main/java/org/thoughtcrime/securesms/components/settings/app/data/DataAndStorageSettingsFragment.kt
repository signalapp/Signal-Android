package org.thoughtcrime.securesms.components.settings.app.data

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalPreview
import org.signal.core.ui.compose.Texts
import org.signal.core.util.bytes
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.compose.rememberStatusBarColorNestedScrollModifier
import org.thoughtcrime.securesms.mms.SentMediaQuality
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import org.thoughtcrime.securesms.webrtc.CallDataMode
import kotlin.math.abs

class DataAndStorageSettingsFragment : ComposeFragment() {

  private val viewModel: DataAndStorageSettingsViewModel by viewModels(
    factoryProducer = {
      val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
      val repository = DataAndStorageSettingsRepository()
      DataAndStorageSettingsViewModel.Factory(preferences, repository)
    }
  )

  override fun onResume() {
    super.onResume()
    viewModel.refresh()
  }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val callbacks = remember { Callbacks() }

    DataAndStorageSettingsScreen(
      state = state,
      callbacks = callbacks
    )
  }

  private inner class Callbacks : DataAndStorageSettingsCallbacks {
    override fun onNavigationClick() {
      requireActivity().onBackPressedDispatcher.onBackPressed()
    }

    override fun onManageStorageClick() {
      findNavController().safeNavigate(R.id.action_dataAndStorageSettingsFragment_to_storagePreferenceFragment)
    }

    override fun onSentMediaQualitySelected(code: String) {
      viewModel.setSentMediaQuality(SentMediaQuality.fromCode(code.toInt()))
    }

    override fun onCallDataModeSelected(code: String) {
      viewModel.setCallDataMode(CallDataMode.fromCode(abs(code.toInt() - 2)))
    }

    override fun onUseProxyClick() {
      findNavController().safeNavigate(R.id.action_dataAndStorageSettingsFragment_to_editProxyFragment)
    }

    override fun onMobileDataAutoDownloadSelectionChanged(selection: Array<String>) {
      viewModel.setMobileAutoDownloadValues(selection.toSet())
    }

    override fun onWifiDataAutoDownloadSelectionChanged(selection: Array<String>) {
      viewModel.setWifiAutoDownloadValues(selection.toSet())
    }

    override fun onRoamingDataAutoDownloadSelectionChanged(selection: Array<String>) {
      viewModel.setRoamingAutoDownloadValues(selection.toSet())
    }
  }
}

private interface DataAndStorageSettingsCallbacks {
  fun onNavigationClick() = Unit
  fun onManageStorageClick() = Unit
  fun onSentMediaQualitySelected(code: String) = Unit
  fun onCallDataModeSelected(code: String) = Unit
  fun onUseProxyClick() = Unit
  fun onMobileDataAutoDownloadSelectionChanged(selection: Array<String>) = Unit
  fun onWifiDataAutoDownloadSelectionChanged(selection: Array<String>) = Unit
  fun onRoamingDataAutoDownloadSelectionChanged(selection: Array<String>) = Unit

  object Empty : DataAndStorageSettingsCallbacks
}

@Composable
private fun DataAndStorageSettingsScreen(
  state: DataAndStorageSettingsState,
  callbacks: DataAndStorageSettingsCallbacks
) {
  Scaffolds.Settings(
    title = stringResource(R.string.preferences__data_and_storage),
    onNavigationClick = callbacks::onNavigationClick,
    navigationIcon = ImageVector.vectorResource(R.drawable.symbol_arrow_start_24)
  ) { paddingValues ->
    LazyColumn(
      modifier = Modifier
        .padding(paddingValues)
        .then(rememberStatusBarColorNestedScrollModifier())
    ) {
      item {
        Rows.TextRow(
          text = stringResource(R.string.preferences_data_and_storage__manage_storage),
          label = state.totalStorageUse.bytes.toUnitString(),
          onClick = callbacks::onManageStorageClick
        )
      }

      item {
        Dividers.Default()
      }

      item {
        Texts.SectionHeader(stringResource(R.string.preferences_chats__media_auto_download))
      }

      item {
        Rows.MultiSelectRow(
          text = stringResource(R.string.preferences_chats__when_using_mobile_data),
          labels = stringArrayResource(R.array.pref_media_download_entries),
          values = stringArrayResource(R.array.pref_media_download_values),
          selection = state.mobileAutoDownloadValues.toTypedArray(),
          onSelectionChanged = callbacks::onMobileDataAutoDownloadSelectionChanged
        )
      }

      item {
        Rows.MultiSelectRow(
          text = stringResource(R.string.preferences_chats__when_using_wifi),
          labels = stringArrayResource(R.array.pref_media_download_entries),
          values = stringArrayResource(R.array.pref_media_download_values),
          selection = state.wifiAutoDownloadValues.toTypedArray(),
          onSelectionChanged = callbacks::onWifiDataAutoDownloadSelectionChanged
        )
      }

      item {
        Rows.MultiSelectRow(
          text = stringResource(R.string.preferences_chats__when_roaming),
          labels = stringArrayResource(R.array.pref_media_download_entries),
          values = stringArrayResource(R.array.pref_media_download_values),
          selection = state.roamingAutoDownloadValues.toTypedArray(),
          onSelectionChanged = callbacks::onRoamingDataAutoDownloadSelectionChanged
        )
      }

      item {
        Dividers.Default()
      }

      item {
        Texts.SectionHeader(stringResource(R.string.DataAndStorageSettingsFragment__media_quality))
      }

      item {
        val context = LocalContext.current
        val labels = remember { SentMediaQuality.getLabels(context) }

        Rows.RadioListRow(
          text = stringResource(R.string.DataAndStorageSettingsFragment__sent_media_quality),
          labels = labels,
          values = SentMediaQuality.entries.map { it.code.toString() }.toTypedArray(),
          selectedValue = state.sentMediaQuality.code.toString(),
          onSelected = callbacks::onSentMediaQualitySelected
        )
      }

      item {
        Rows.TextRow(
          text = {
            Text(
              text = stringResource(R.string.DataAndStorageSettingsFragment__sending_high_quality_media_will_use_more_data),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        )
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
          labels = stringArrayResource(R.array.pref_data_and_storage_call_data_mode_values),
          values = CallDataMode.entries.map { it.code.toString() }.toTypedArray(),
          selectedValue = abs(state.callDataMode.code - 2).toString(),
          onSelected = callbacks::onCallDataModeSelected
        )
      }

      item {
        Rows.TextRow(
          text = {
            Text(
              text = stringResource(R.string.preference_data_and_storage__using_less_data_may_improve_calls_on_bad_networks),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
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
          label = stringResource(if (state.isProxyEnabled) R.string.preferences_on else R.string.preferences_off),
          onClick = callbacks::onUseProxyClick
        )
      }
    }
  }
}

@SignalPreview
@Composable
private fun DataAndStorageSettingsScreenPreview() {
  Previews.Preview {
    DataAndStorageSettingsScreen(
      state = DataAndStorageSettingsState(
        totalStorageUse = 100_000,
        mobileAutoDownloadValues = setOf(),
        wifiAutoDownloadValues = setOf(),
        roamingAutoDownloadValues = setOf(),
        callDataMode = CallDataMode.HIGH_ALWAYS,
        isProxyEnabled = false,
        sentMediaQuality = SentMediaQuality.STANDARD
      ),
      callbacks = DataAndStorageSettingsCallbacks.Empty
    )
  }
}
