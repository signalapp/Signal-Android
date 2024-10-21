/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.storage

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.integerArrayResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.compose.rememberNavController
import androidx.navigation.fragment.findNavController
import androidx.navigation.navArgument
import org.signal.core.ui.Animations
import org.signal.core.ui.Dialogs
import org.signal.core.ui.Dividers
import org.signal.core.ui.Previews
import org.signal.core.ui.Rows
import org.signal.core.ui.Rows.TextAndLabel
import org.signal.core.ui.Scaffolds
import org.signal.core.ui.SignalPreview
import org.signal.core.ui.Texts
import org.signal.core.ui.theme.SignalTheme
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.billing.upgrade.UpgradeToEnableOptimizedStorageSheet
import org.thoughtcrime.securesms.billing.upgrade.UpgradeToPaidTierBottomSheet
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.database.MediaTable
import org.thoughtcrime.securesms.keyvalue.KeepMessagesDuration
import org.thoughtcrime.securesms.mediaoverview.MediaOverviewActivity
import org.thoughtcrime.securesms.preferences.widgets.StorageGraphView
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.BottomSheetUtil
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.thoughtcrime.securesms.util.Util
import org.thoughtcrime.securesms.util.viewModel
import java.text.NumberFormat

/**
 * Manage settings related to on-device storage including viewing usage and auto-delete settings.
 */
class ManageStorageSettingsFragment : ComposeFragment() {

  private val viewModel by viewModel<ManageStorageSettingsViewModel> { ManageStorageSettingsViewModel() }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    UpgradeToPaidTierBottomSheet.addResultListener(this) {
      viewModel.setOptimizeStorage(true)
    }
  }

  @ExperimentalMaterial3Api
  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsState()

    val navController = rememberNavController()

    SignalTheme {
      NavHost(
        navController = navController,
        startDestination = "manage",
        enterTransition = { Animations.navHostSlideInTransition { it } },
        exitTransition = { Animations.navHostSlideOutTransition { -it } },
        popEnterTransition = { Animations.navHostSlideInTransition { -it } },
        popExitTransition = { Animations.navHostSlideOutTransition { it } }
      ) {
        composable("manage") {
          ManageStorageSettingsScreen(
            state = state,
            onNavigationClick = { findNavController().popBackStack() },
            onReviewStorage = { startActivity(MediaOverviewActivity.forAll(requireContext())) },
            onSetKeepMessages = { navController.navigate("set-keep-messages") },
            onSetChatLengthLimit = { navController.navigate("set-chat-length-limit") },
            onSyncTrimThreadDeletes = { viewModel.setSyncTrimDeletes(it) },
            onDeleteChatHistory = { navController.navigate("confirm-delete-chat-history") },
            onToggleOnDeviceStorageOptimization = { enabled ->
              if (state.isPaidTierPending) {
                navController.navigate("paid-tier-pending")
              } else if (state.onDeviceStorageOptimizationState == ManageStorageSettingsViewModel.OnDeviceStorageOptimizationState.REQUIRES_PAID_TIER) {
                UpgradeToEnableOptimizedStorageSheet().show(parentFragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
              } else {
                viewModel.setOptimizeStorage(enabled)
              }
            }
          )
        }

        composable("set-keep-messages") {
          SetKeepMessagesScreen(
            selection = state.keepMessagesDuration,
            onNavigationClick = { navController.popBackStack() },
            onSelectionChanged = { newDuration ->
              if (viewModel.showConfirmKeepDurationChange(newDuration)) {
                navController.navigate("confirm-set-keep-messages/${newDuration.id}")
              } else {
                viewModel.setKeepMessagesDuration(newDuration)
              }
            }
          )
        }

        composable("set-chat-length-limit") {
          SetChatLengthLimitScreen(
            currentLimit = state.lengthLimit,
            onNavigationClick = { navController.popBackStack() },
            onOptionSelected = { newLengthLimit ->
              if (viewModel.showConfirmSetChatLengthLimit(newLengthLimit)) {
                navController.navigate("confirm-set-length-limit/$newLengthLimit")
              } else {
                viewModel.setChatLengthLimit(newLengthLimit)
              }
            },
            onCustomSelected = { navController.navigate("custom-set-length-limit") }
          )
        }

        dialog("confirm-delete-chat-history") {
          Dialogs.SimpleAlertDialog(
            title = stringResource(id = R.string.preferences_storage__delete_message_history),
            body = if (TextSecurePreferences.isMultiDevice(LocalContext.current) && Recipient.self().deleteSyncCapability.isSupported) {
              stringResource(id = R.string.preferences_storage__this_will_delete_all_message_history_and_media_from_your_device_linked_device)
            } else {
              stringResource(id = R.string.preferences_storage__this_will_delete_all_message_history_and_media_from_your_device)
            },
            confirm = stringResource(id = R.string.delete),
            confirmColor = MaterialTheme.colorScheme.error,
            dismiss = stringResource(id = android.R.string.cancel),
            onConfirm = { navController.navigate("double-confirm-delete-chat-history") },
            onDismiss = { navController.popBackStack() }
          )
        }

        dialog("double-confirm-delete-chat-history", dialogProperties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)) {
          Dialogs.SimpleAlertDialog(
            title = stringResource(id = R.string.preferences_storage__are_you_sure_you_want_to_delete_all_message_history),
            body = if (TextSecurePreferences.isMultiDevice(LocalContext.current) && Recipient.self().deleteSyncCapability.isSupported) {
              stringResource(id = R.string.preferences_storage__all_message_history_will_be_permanently_removed_this_action_cannot_be_undone_linked_device)
            } else {
              stringResource(id = R.string.preferences_storage__all_message_history_will_be_permanently_removed_this_action_cannot_be_undone)
            },
            confirm = stringResource(id = R.string.preferences_storage__delete_all_now),
            confirmColor = MaterialTheme.colorScheme.error,
            dismiss = stringResource(id = android.R.string.cancel),
            onConfirm = { viewModel.deleteChatHistory() },
            onDismiss = { navController.popBackStack() }
          )
        }

        dialog(
          route = "confirm-set-keep-messages/{keepMessagesId}",
          arguments = listOf(navArgument("keepMessagesId") { type = NavType.IntType })
        ) {
          val newDuration = KeepMessagesDuration.fromId(it.arguments!!.getInt("keepMessagesId"))

          Dialogs.SimpleAlertDialog(
            title = stringResource(id = R.string.preferences_storage__delete_older_messages),
            body = stringResource(id = R.string.preferences_storage__this_will_permanently_delete_all_message_history_and_media, stringResource(id = newDuration.stringResource)),
            confirm = stringResource(id = R.string.delete),
            dismiss = stringResource(id = android.R.string.cancel),
            onConfirm = { viewModel.setKeepMessagesDuration(newDuration) },
            onDismiss = { navController.popBackStack() }
          )
        }

        dialog(
          route = "confirm-set-length-limit/{lengthLimit}",
          arguments = listOf(navArgument("lengthLimit") { type = NavType.IntType })
        ) {
          val newLengthLimit = it.arguments!!.getInt("lengthLimit")

          Dialogs.SimpleAlertDialog(
            title = stringResource(id = R.string.preferences_storage__delete_older_messages),
            body = pluralStringResource(
              id = R.plurals.preferences_storage__this_will_permanently_trim_all_conversations_to_the_d_most_recent_messages,
              count = newLengthLimit,
              NumberFormat.getInstance().format(newLengthLimit)
            ),
            confirm = stringResource(id = R.string.delete),
            dismiss = stringResource(id = android.R.string.cancel),
            onConfirm = { viewModel.setChatLengthLimit(newLengthLimit) },
            onDismiss = { navController.popBackStack() }
          )
        }

        dialog(
          route = "custom-set-length-limit"
        ) {
          SetCustomLengthLimitDialog(
            currentLimit = if (state.lengthLimit != ManageStorageSettingsViewModel.ManageStorageState.NO_LIMIT) state.lengthLimit else null,
            onCustomLimitSet = { newLengthLimit ->
              if (viewModel.showConfirmSetChatLengthLimit(newLengthLimit)) {
                navController.navigate("confirm-set-length-limit/$newLengthLimit")
              } else {
                viewModel.setChatLengthLimit(newLengthLimit)
              }
            },
            onDismiss = { navController.popBackStack() }
          )
        }

        dialog(
          route = "paid-tier-pending"
        ) {
          // TODO [backups] Finalized copy
          Dialogs.SimpleAlertDialog(
            title = "Paid tier pending",
            body = "TODO",
            confirm = stringResource(android.R.string.ok),
            onConfirm = {},
            onDismiss = { navController.popBackStack() }
          )
        }
      }
    }
  }

  override fun onResume() {
    super.onResume()
    viewModel.refresh()
  }
}

@Composable
private fun ManageStorageSettingsScreen(
  state: ManageStorageSettingsViewModel.ManageStorageState,
  onNavigationClick: () -> Unit = {},
  onReviewStorage: () -> Unit = {},
  onSetKeepMessages: () -> Unit = {},
  onSetChatLengthLimit: () -> Unit = {},
  onSyncTrimThreadDeletes: (Boolean) -> Unit = {},
  onDeleteChatHistory: () -> Unit = {},
  onToggleOnDeviceStorageOptimization: (Boolean) -> Unit = {}
) {
  Scaffolds.Settings(
    title = stringResource(id = R.string.preferences__storage),
    onNavigationClick = onNavigationClick,
    navigationIconPainter = painterResource(id = R.drawable.ic_arrow_left_24)
  ) { contentPadding ->
    Column(
      modifier = Modifier
        .padding(contentPadding)
        .verticalScroll(rememberScrollState())
    ) {
      Texts.SectionHeader(text = stringResource(id = R.string.preferences_storage__storage_usage))

      StorageOverview(state.breakdown, onReviewStorage)

      if (state.onDeviceStorageOptimizationState > ManageStorageSettingsViewModel.OnDeviceStorageOptimizationState.FEATURE_NOT_AVAILABLE) {
        Dividers.Default()

        Texts.SectionHeader(text = stringResource(id = R.string.ManageStorageSettingsFragment__on_device_storage))

        Rows.ToggleRow(
          checked = state.onDeviceStorageOptimizationState == ManageStorageSettingsViewModel.OnDeviceStorageOptimizationState.ENABLED,
          text = stringResource(id = R.string.ManageStorageSettingsFragment__optimize_on_device_storage),
          label = stringResource(id = R.string.ManageStorageSettingsFragment__unused_media_will_be_offloaded),
          onCheckChanged = onToggleOnDeviceStorageOptimization
        )
      }

      Dividers.Default()

      Texts.SectionHeader(text = stringResource(id = R.string.ManageStorageSettingsFragment_chat_limit))

      Rows.TextRow(
        text = stringResource(id = R.string.preferences__keep_messages),
        label = stringResource(id = state.keepMessagesDuration.stringResource),
        onClick = onSetKeepMessages
      )

      Rows.TextRow(
        text = stringResource(id = R.string.preferences__conversation_length_limit),
        label = if (state.lengthLimit != ManageStorageSettingsViewModel.ManageStorageState.NO_LIMIT) {
          pluralStringResource(
            id = R.plurals.preferences_storage__s_messages_plural,
            count = state.lengthLimit,
            NumberFormat.getInstance().format(state.lengthLimit)
          )
        } else {
          stringResource(id = R.string.preferences_storage__none)
        },
        onClick = onSetChatLengthLimit
      )

      Rows.ToggleRow(
        text = stringResource(id = R.string.ManageStorageSettingsFragment_apply_limits_title),
        label = stringResource(id = R.string.ManageStorageSettingsFragment_apply_limits_description),
        checked = state.syncTrimDeletes,
        onCheckChanged = onSyncTrimThreadDeletes
      )

      Dividers.Default()

      Rows.TextRow(
        text = stringResource(id = R.string.ManageStorageSettingsFragment_delete_message_history),
        onClick = onDeleteChatHistory
      )
    }
  }
}

@Composable
private fun StorageOverview(
  breakdown: MediaTable.StorageBreakdown?,
  onReviewStorage: () -> Unit
) {
  AndroidView(
    factory = {
      LayoutInflater.from(it).inflate(R.layout.preference_storage_category, null)
    },
    modifier = Modifier.fillMaxWidth()
  ) {
    if (breakdown != null) {
      val breakdownEntries = StorageGraphView.StorageBreakdown(
        listOf(
          StorageGraphView.Entry(ContextCompat.getColor(it.context, R.color.storage_color_photos), breakdown.photoSize),
          StorageGraphView.Entry(ContextCompat.getColor(it.context, R.color.storage_color_videos), breakdown.videoSize),
          StorageGraphView.Entry(ContextCompat.getColor(it.context, R.color.storage_color_files), breakdown.documentSize),
          StorageGraphView.Entry(ContextCompat.getColor(it.context, R.color.storage_color_audio), breakdown.audioSize)
        )
      )

      it.findViewById<StorageGraphView>(R.id.storageGraphView).setStorageBreakdown(breakdownEntries)
      it.findViewById<TextView>(R.id.total_size).text = Util.getPrettyFileSize(breakdownEntries.totalSize)
    }

    it.findViewById<View>(R.id.free_up_space).setOnClickListener {
      onReviewStorage()
    }
  }
}

@Composable
private fun SetKeepMessagesScreen(
  selection: KeepMessagesDuration,
  onNavigationClick: () -> Unit = {},
  onSelectionChanged: (KeepMessagesDuration) -> Unit = {}
) {
  Scaffolds.Settings(
    title = stringResource(id = R.string.preferences__keep_messages),
    onNavigationClick = onNavigationClick,
    navigationIconPainter = painterResource(id = R.drawable.ic_arrow_left_24)
  ) { contentPadding ->
    Column(
      modifier = Modifier
        .padding(contentPadding)
        .verticalScroll(rememberScrollState())
    ) {
      KeepMessagesDuration
        .values()
        .forEach {
          Rows.RadioRow(
            text = stringResource(id = it.stringResource),
            selected = it == selection,
            modifier = Modifier.clickable { onSelectionChanged(it) }
          )
        }

      Rows.TextRow(
        text = {
          Text(
            text = stringResource(id = R.string.ManageStorageSettingsFragment_keep_messages_duration_warning),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      )
    }
  }
}

@Composable
private fun SetChatLengthLimitScreen(
  currentLimit: Int,
  onNavigationClick: () -> Unit = {},
  onOptionSelected: (Int) -> Unit = {},
  onCustomSelected: (Int) -> Unit = {}
) {
  Scaffolds.Settings(
    title = stringResource(id = R.string.preferences__conversation_length_limit),
    onNavigationClick = onNavigationClick,
    navigationIconPainter = painterResource(id = R.drawable.ic_arrow_left_24)
  ) { contentPadding ->
    Column(
      modifier = Modifier
        .padding(contentPadding)
        .verticalScroll(rememberScrollState())
    ) {
      val options = integerArrayResource(id = R.array.conversation_length_limit)
      var customSelected = true

      for (option in options) {
        val isSelected = option == currentLimit

        Rows.RadioRow(
          selected = isSelected,
          text = if (option == 0) {
            stringResource(id = R.string.preferences_storage__none)
          } else {
            pluralStringResource(id = R.plurals.preferences_storage__s_messages_plural, count = option, NumberFormat.getInstance().format(option.toLong()))
          },
          modifier = Modifier.clickable { onOptionSelected(option) }
        )

        if (isSelected) {
          customSelected = false
        }
      }

      Rows.RadioRow(
        content = {
          TextAndLabel(
            text = stringResource(id = R.string.preferences_storage__custom),
            label = if (customSelected) {
              pluralStringResource(id = R.plurals.preferences_storage__s_messages_plural, count = currentLimit, NumberFormat.getInstance().format(currentLimit))
            } else {
              null
            }
          )

          if (customSelected) {
            Dividers.Vertical(
              modifier = Modifier
                .padding(horizontal = 8.dp)
                .height(48.dp)
            )

            Icon(
              painter = painterResource(id = R.drawable.symbol_settings_android_24),
              contentDescription = null,
              modifier = Modifier
                .clickable { onCustomSelected(currentLimit) }
                .padding(12.dp)
            )
          }
        },
        selected = customSelected,
        modifier = Modifier.clickable { onCustomSelected(currentLimit) }
      )

      Rows.TextRow(
        text = {
          Text(
            text = stringResource(id = R.string.ManageStorageSettingsFragment_chat_length_limit_warning),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      )
    }
  }
}

@Composable
private fun SetCustomLengthLimitDialog(
  currentLimit: Int? = 1000,
  onCustomLimitSet: (Int) -> Unit = {},
  onDismiss: () -> Unit = {}
) {
  var lengthLimit by remember {
    mutableStateOf(
      TextFieldValue(
        text = currentLimit?.toString() ?: "",
        selection = TextRange(currentLimit.toString().length)
      )
    )
  }

  val focusRequester = remember { FocusRequester() }

  androidx.compose.material3.AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(text = stringResource(id = R.string.preferences__conversation_length_limit)) },
    text = {
      TextField(
        value = lengthLimit,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        onValueChange = { value ->
          val cleaned = value.text.replace("\\D".toRegex(), "")
          lengthLimit = if (cleaned == value.text) {
            value
          } else {
            value.copy(text = cleaned)
          }
        },
        modifier = Modifier.focusRequester(focusRequester)
      )

      LaunchedEffect(Unit) {
        focusRequester.requestFocus()
      }
    },
    confirmButton = {
      TextButton(
        enabled = lengthLimit.text.toIntOrNull() != null,
        onClick = {
          onDismiss()
          onCustomLimitSet(lengthLimit.text.toInt())
        }
      ) {
        Text(text = stringResource(id = android.R.string.ok))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(text = stringResource(id = android.R.string.cancel))
      }
    }
  )
}

@SignalPreview
@Composable
private fun ManageStorageSettingsScreenPreview() {
  Previews.Preview {
    ManageStorageSettingsScreen(
      state = ManageStorageSettingsViewModel.ManageStorageState(
        keepMessagesDuration = KeepMessagesDuration.FOREVER,
        lengthLimit = ManageStorageSettingsViewModel.ManageStorageState.NO_LIMIT,
        syncTrimDeletes = true,
        onDeviceStorageOptimizationState = ManageStorageSettingsViewModel.OnDeviceStorageOptimizationState.DISABLED
      )
    )
  }
}

@SignalPreview
@Composable
private fun SetKeepMessagesScreenPreview() {
  Previews.Preview {
    SetKeepMessagesScreen(selection = KeepMessagesDuration.FOREVER)
  }
}

@SignalPreview
@Composable
private fun SetChatLengthLimitScreenPreview() {
  Previews.Preview {
    SetChatLengthLimitScreen(
      currentLimit = 1000
    )
  }
}

@SignalPreview
@Composable
private fun SetCustomLengthLimitDialogPreview() {
  Previews.Preview {
    SetCustomLengthLimitDialog(currentLimit = 123)
  }
}
