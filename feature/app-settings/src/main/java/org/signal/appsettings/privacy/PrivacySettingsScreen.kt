/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.privacy

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.flow.first
import org.signal.appsettings.R
import org.signal.core.ui.biometrics.rememberBiometricsAuthentication
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.DurationPickerUnit
import org.signal.core.ui.compose.Pickers
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.SignalPreviewWrapper
import org.signal.core.ui.compose.Snackbars
import org.signal.core.ui.compose.Texts
import org.signal.core.ui.util.ExpirationUtil
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import org.signal.core.ui.R as CoreUiR

@VisibleForTesting
object PrivacySettingsTestTags {
  const val SCROLLER = "scroller"
  const val ROW_PHONE_NUMBER = "row-phone-number"
  const val ROW_BLOCKED = "row-blocked"
  const val ROW_READ_RECEIPTS = "row-read-receipts"
  const val ROW_TYPING_INDICATORS = "row-typing-indicators"
  const val ROW_DEFAULT_TIMER = "row-default-timer"
  const val ROW_ENABLE_PASSPHRASE = "row-enable-passphrase"
  const val ROW_CHANGE_PASSPHRASE = "row-change-passphrase"
  const val ROW_PASSPHRASE_TIMEOUT = "row-passphrase-timeout"
  const val ROW_PASSPHRASE_TIMEOUT_INTERVAL = "row-passphrase-timeout-interval"
  const val ROW_SCREEN_LOCK = "row-screen-lock"
  const val ROW_SCREEN_SECURITY = "row-screen-security"
  const val ROW_INCOGNITO_KEYBOARD = "row-incognito-keyboard"
  const val TEXT_INCOGNITO_KEYBOARD_DISCLAIMER = "text-incognito-keyboard-disclaimer"
  const val ROW_PAYMENT_LOCK = "row-payment-lock"
  const val ROW_ADVANCED = "row-advanced"
}

private val PASSPHRASE_TIMEOUT_UNITS = listOf(DurationPickerUnit.MINUTES, DurationPickerUnit.HOURS)

/**
 * The top level privacy settings: who can see the user's number, blocked contacts, messaging privacy, disappearing
 * message defaults, app security and payment lock.
 */
@Composable
fun PrivacySettingsScreen(
  state: PrivacySettingsState,
  onEvent: (PrivacySettingsEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  LifecycleResumeEffect(Unit) {
    onEvent(PrivacySettingsEvent.ScreenResumed)
    onPauseOrDispose {}
  }

  val listState = rememberLazyListState()

  if (state.scrollToPaymentLock) {
    LaunchedEffect(Unit) {
      scrollToPaymentLock(listState)
      onEvent(PrivacySettingsEvent.ScrolledToPaymentLock)
    }
  }

  Scaffolds.Settings(
    title = stringResource(R.string.PrivacySettingsScreen__privacy),
    onNavigationClick = { onEvent(PrivacySettingsEvent.NavigateBackClicked) },
    navigationIcon = SignalIcons.ArrowStart.imageVector,
    navigationContentDescription = stringResource(CoreUiR.string.CloseButton__content_description),
    snackbarHost = {
      Snackbars.Host(
        message = state.snackbar.message(),
        onDismissed = { onEvent(PrivacySettingsEvent.SnackbarDismissed) }
      )
    },
    modifier = modifier
  ) { paddingValues ->
    LazyColumn(
      state = listState,
      modifier = Modifier
        .padding(paddingValues)
        .testTag(PrivacySettingsTestTags.SCROLLER)
    ) {
      if (state.hasPhoneNumber) {
        item {
          Rows.TextRow(
            text = stringResource(R.string.preferences_app_protection__phone_number),
            label = stringResource(R.string.preferences_app_protection__choose_who_can_see),
            onClick = { onEvent(PrivacySettingsEvent.PhoneNumberClicked) },
            modifier = Modifier.testTag(PrivacySettingsTestTags.ROW_PHONE_NUMBER)
          )
        }

        item {
          Dividers.Default()
        }
      }

      item {
        Rows.TextRow(
          text = stringResource(R.string.PrivacySettingsFragment__blocked),
          label = pluralStringResource(R.plurals.PrivacySettingsFragment__d_contacts, state.blockedCount, state.blockedCount),
          onClick = { onEvent(PrivacySettingsEvent.BlockedClicked) },
          modifier = Modifier.testTag(PrivacySettingsTestTags.ROW_BLOCKED)
        )
      }

      item {
        Dividers.Default()
      }

      item {
        Texts.SectionHeader(text = stringResource(R.string.PrivacySettingsFragment__messaging))
      }

      item {
        Rows.ToggleRow(
          checked = state.readReceipts,
          text = stringResource(R.string.preferences__read_receipts),
          label = stringResource(R.string.preferences__if_read_receipts_are_disabled_you_wont_be_able_to_see_read_receipts),
          onCheckChanged = { onEvent(PrivacySettingsEvent.ReadReceiptsToggled(it)) },
          modifier = Modifier.testTag(PrivacySettingsTestTags.ROW_READ_RECEIPTS)
        )
      }

      item {
        Rows.ToggleRow(
          checked = state.typingIndicators,
          text = stringResource(R.string.preferences__typing_indicators),
          label = stringResource(R.string.preferences__if_typing_indicators_are_disabled_you_wont_be_able_to_see_typing_indicators),
          onCheckChanged = { onEvent(PrivacySettingsEvent.TypingIndicatorsToggled(it)) },
          modifier = Modifier.testTag(PrivacySettingsTestTags.ROW_TYPING_INDICATORS)
        )
      }

      item {
        Dividers.Default()
      }

      item {
        Texts.SectionHeader(text = stringResource(R.string.PrivacySettingsScreen__disappearing_messages))
      }

      item {
        DefaultTimerRow(
          timer = state.universalExpireTimer,
          onClick = { onEvent(PrivacySettingsEvent.DefaultTimerClicked) }
        )
      }

      item {
        Dividers.Default()
      }

      item {
        Texts.SectionHeader(text = stringResource(R.string.PrivacySettingsFragment__app_security))
      }

      if (state.isObsoletePassphraseEnabled) {
        obsoletePassphraseRows(state = state, onEvent = onEvent)
      } else {
        item {
          Rows.TextRow(
            text = stringResource(R.string.preferences_app_protection__screen_lock),
            label = screenLockSummary(state),
            enabled = state.isDeviceSecure,
            onClick = { onEvent(PrivacySettingsEvent.ScreenLockClicked) },
            onDisabledClick = { onEvent(PrivacySettingsEvent.DisabledScreenLockClicked) },
            modifier = Modifier.testTag(PrivacySettingsTestTags.ROW_SCREEN_LOCK)
          )
        }
      }

      item {
        Rows.ToggleRow(
          checked = state.screenSecurity,
          text = stringResource(R.string.preferences__screen_security),
          label = stringResource(R.string.PrivacySettingsFragment__block_screenshots_in_the_recents_list_and_inside_the_app),
          onCheckChanged = { onEvent(PrivacySettingsEvent.ScreenSecurityToggled(it)) },
          modifier = Modifier.testTag(PrivacySettingsTestTags.ROW_SCREEN_SECURITY)
        )
      }

      item {
        Rows.ToggleRow(
          checked = state.incognitoKeyboard,
          text = stringResource(R.string.preferences__incognito_keyboard),
          label = stringResource(R.string.preferences__request_keyboard_to_disable),
          onCheckChanged = { onEvent(PrivacySettingsEvent.IncognitoKeyboardToggled(it)) },
          modifier = Modifier.testTag(PrivacySettingsTestTags.ROW_INCOGNITO_KEYBOARD)
        )
      }

      item {
        IncognitoKeyboardDisclaimer(onEvent = onEvent)
      }

      item {
        Dividers.Default()
      }

      item {
        Texts.SectionHeader(text = stringResource(R.string.preferences_app_protection__payments))
      }

      item {
        PaymentLockRow(state = state, onEvent = onEvent)
      }

      item {
        Dividers.Default()
      }

      item {
        Rows.TextRow(
          text = stringResource(R.string.PrivacySettingsScreen__advanced),
          label = stringResource(R.string.PrivacySettingsFragment__signal_message_and_calls),
          onClick = { onEvent(PrivacySettingsEvent.AdvancedClicked) },
          modifier = Modifier.testTag(PrivacySettingsTestTags.ROW_ADVANCED)
        )
      }
    }

    when (state.dialog) {
      PrivacySettingsState.Dialog.None -> Unit
      PrivacySettingsState.Dialog.ConfirmDisablePassphrase -> {
        Dialogs.SimpleAlertDialog(
          title = stringResource(R.string.ApplicationPreferencesActivity_disable_passphrase),
          body = stringResource(R.string.ApplicationPreferencesActivity_this_will_permanently_unlock_signal_and_message_notifications),
          confirm = stringResource(R.string.ApplicationPreferencesActivity_disable),
          dismiss = stringResource(android.R.string.cancel),
          onConfirm = { onEvent(PrivacySettingsEvent.DisablePassphraseConfirmed) },
          onDeny = { onEvent(PrivacySettingsEvent.DialogDismissed) },
          onDismissRequest = { onEvent(PrivacySettingsEvent.DialogDismissed) }
        )
      }
      PrivacySettingsState.Dialog.PassphraseTimeout -> {
        Pickers.DurationDialog(
          title = stringResource(R.string.preferences__inactivity_timeout_interval),
          initialDuration = state.obsoletePassphraseTimeout,
          units = PASSPHRASE_TIMEOUT_UNITS,
          onConfirm = { onEvent(PrivacySettingsEvent.PassphraseTimeoutSelected(it)) },
          onDismiss = { onEvent(PrivacySettingsEvent.DialogDismissed) }
        )
      }
      PrivacySettingsState.Dialog.PaymentLockNeedsDeviceSecurity -> {
        Dialogs.SimpleAlertDialog(
          title = stringResource(R.string.PrivacySettingsFragment__cant_enable_title),
          body = stringResource(R.string.PrivacySettingsFragment__cant_enable_description),
          confirm = stringResource(R.string.PrivacySettingsScreen__turn_on),
          dismiss = stringResource(R.string.PrivacySettingsScreen__not_now),
          onConfirm = { onEvent(PrivacySettingsEvent.OpenDeviceSecuritySettingsClicked) },
          onDeny = { onEvent(PrivacySettingsEvent.DialogDismissed) },
          onDismissRequest = { onEvent(PrivacySettingsEvent.DialogDismissed) }
        )
      }
    }
  }
}

/** Payment lock is the second to last row, so landing on the last row brings it fully into view. */
private suspend fun scrollToPaymentLock(listState: LazyListState) {
  val itemCount = snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > 0 }
  listState.scrollToItem(itemCount - 1)
}

private fun LazyListScope.obsoletePassphraseRows(
  state: PrivacySettingsState,
  onEvent: (PrivacySettingsEvent) -> Unit
) {
  item {
    Rows.ToggleRow(
      checked = true,
      text = stringResource(R.string.preferences__enable_passphrase),
      label = stringResource(R.string.preferences__lock_signal_and_message_notifications_with_a_passphrase),
      onCheckChanged = { onEvent(PrivacySettingsEvent.DisablePassphraseClicked) },
      modifier = Modifier.testTag(PrivacySettingsTestTags.ROW_ENABLE_PASSPHRASE)
    )
  }

  item {
    Rows.TextRow(
      text = stringResource(R.string.preferences__change_passphrase),
      label = stringResource(R.string.preferences__change_your_passphrase),
      onClick = { onEvent(PrivacySettingsEvent.ChangePassphraseClicked) },
      modifier = Modifier.testTag(PrivacySettingsTestTags.ROW_CHANGE_PASSPHRASE)
    )
  }

  item {
    Rows.ToggleRow(
      checked = state.isObsoletePassphraseTimeoutEnabled,
      text = stringResource(R.string.preferences__inactivity_timeout_passphrase),
      label = stringResource(R.string.preferences__auto_lock_signal_after_a_specified_time_interval_of_inactivity),
      onCheckChanged = { onEvent(PrivacySettingsEvent.PassphraseTimeoutToggled(it)) },
      modifier = Modifier.testTag(PrivacySettingsTestTags.ROW_PASSPHRASE_TIMEOUT)
    )
  }

  item {
    Rows.TextRow(
      text = stringResource(R.string.preferences__inactivity_timeout_interval),
      onClick = { onEvent(PrivacySettingsEvent.PassphraseTimeoutIntervalClicked) },
      modifier = Modifier.testTag(PrivacySettingsTestTags.ROW_PASSPHRASE_TIMEOUT_INTERVAL)
    )
  }
}

@Composable
private fun screenLockSummary(state: PrivacySettingsState): String {
  return if (state.isScreenLockOn) {
    ExpirationUtil.getExpirationDisplayValue(LocalContext.current, state.effectiveScreenLockTimeout.inWholeSeconds.toInt())
  } else {
    stringResource(R.string.ScreenLockSettingsFragment__off)
  }
}

@Composable
private fun DefaultTimerRow(
  timer: Duration,
  onClick: () -> Unit
) {
  val context = LocalContext.current
  val seconds = timer.inWholeSeconds.toInt()
  val value = ExpirationUtil.getExpirationAbbreviatedDisplayValue(context, seconds)
  val valueDescription = ExpirationUtil.getExpirationDisplayValue(context, seconds)

  Rows.TextRow(
    text = {
      with(Rows) {
        TextAndLabel(
          text = stringResource(R.string.PrivacySettingsFragment__default_timer_for_new_changes),
          label = stringResource(R.string.PrivacySettingsFragment__set_a_default_disappearing_message_timer_for_all_new_chats_started_by_you)
        )
      }

      Spacer(Modifier.width(16.dp))

      Text(
        text = value,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.semantics { contentDescription = valueDescription }
      )
    },
    onClick = onClick,
    modifier = Modifier.testTag(PrivacySettingsTestTags.ROW_DEFAULT_TIMER)
  )
}

@Composable
private fun IncognitoKeyboardDisclaimer(onEvent: (PrivacySettingsEvent) -> Unit) {
  Text(
    text = buildAnnotatedString {
      append(stringResource(R.string.preferences__this_setting_is_not_a_guarantee))
      append(" ")

      withLink(
        LinkAnnotation.Clickable(
          tag = "learn-more",
          styles = TextLinkStyles(style = SpanStyle(color = MaterialTheme.colorScheme.onSurface)),
          linkInteractionListener = { onEvent(PrivacySettingsEvent.IncognitoKeyboardLearnMoreClicked) }
        )
      ) {
        append(stringResource(R.string.PrivacySettingsScreen__learn_more))
      }
    },
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier
      .padding(horizontal = dimensionResource(CoreUiR.dimen.gutter), vertical = 16.dp)
      .testTag(PrivacySettingsTestTags.TEXT_INCOGNITO_KEYBOARD_DISCLAIMER)
  )
}

@Composable
private fun PaymentLockRow(
  state: PrivacySettingsState,
  onEvent: (PrivacySettingsEvent) -> Unit
) {
  val disableBiometrics = rememberBiometricsAuthentication(
    promptTitle = stringResource(R.string.PrivacySettingsScreen__signal),
    proceedWhenUnavailable = false
  )

  LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
    disableBiometrics.cancelAuthentication()
  }

  Rows.ToggleRow(
    checked = state.isPaymentLockOn,
    text = stringResource(R.string.preferences__payment_lock),
    label = stringResource(R.string.PrivacySettingsFragment__payment_lock_require_lock),
    onCheckChanged = { enabled ->
      if (enabled) {
        onEvent(PrivacySettingsEvent.EnablePaymentLockClicked)
      } else {
        disableBiometrics.withBiometricsAuthentication { onEvent(PrivacySettingsEvent.PaymentLockDisableAuthenticated) }
      }
    },
    modifier = Modifier.testTag(PrivacySettingsTestTags.ROW_PAYMENT_LOCK)
  )
}

@Composable
private fun PrivacySettingsState.Snackbar.message(): String? {
  return when (this) {
    PrivacySettingsState.Snackbar.None -> null
    PrivacySettingsState.Snackbar.ScreenLockNeedsDeviceSecurity -> stringResource(R.string.preferences_app_protection__to_use_screen_lock)
    PrivacySettingsState.Snackbar.PassphraseNotSet -> stringResource(R.string.ApplicationPreferenceActivity_you_havent_set_a_passphrase_yet)
    PrivacySettingsState.Snackbar.DeviceSecuritySettingsUnavailable -> stringResource(R.string.PrivacySettingsFragment__failed_to_navigate_to_system_settings)
  }
}

private val previewState = PrivacySettingsState(
  blockedCount = 3,
  readReceipts = true,
  typingIndicators = true,
  universalExpireTimer = 1.days,
  screenLock = true,
  screenLockTimeout = 5.minutes,
  screenSecurity = true,
  paymentLock = true
)

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun PrivacySettingsScreenPreview() {
  PrivacySettingsScreen(state = previewState, onEvent = {})
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun PrivacySettingsScreenDefaultsPreview() {
  PrivacySettingsScreen(state = PrivacySettingsState(), onEvent = {})
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun PrivacySettingsScreenInsecureDevicePreview() {
  PrivacySettingsScreen(state = previewState.copy(isDeviceSecure = false, hasPhoneNumber = false), onEvent = {})
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun PrivacySettingsScreenPassphrasePreview() {
  PrivacySettingsScreen(
    state = previewState.copy(isObsoletePassphraseEnabled = true, isObsoletePassphraseTimeoutEnabled = true, obsoletePassphraseTimeout = 2.hours),
    onEvent = {}
  )
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun PrivacySettingsScreenDisablePassphraseDialogPreview() {
  PrivacySettingsScreen(
    state = previewState.copy(isObsoletePassphraseEnabled = true, dialog = PrivacySettingsState.Dialog.ConfirmDisablePassphrase),
    onEvent = {}
  )
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun PrivacySettingsScreenPassphraseTimeoutDialogPreview() {
  PrivacySettingsScreen(
    state = previewState.copy(isObsoletePassphraseEnabled = true, dialog = PrivacySettingsState.Dialog.PassphraseTimeout),
    onEvent = {}
  )
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun PrivacySettingsScreenPaymentLockDialogPreview() {
  PrivacySettingsScreen(
    state = previewState.copy(isDeviceSecure = false, dialog = PrivacySettingsState.Dialog.PaymentLockNeedsDeviceSecurity),
    onEvent = {}
  )
}
