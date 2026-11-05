/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.advancedprivacy

import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import org.signal.appsettings.AppSettingsLazyColumn
import org.signal.appsettings.R
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.SignalPreviewWrapper
import org.signal.core.ui.compose.Texts
import org.signal.core.ui.R as CoreUiR

@VisibleForTesting
object AdvancedPrivacySettingsTestTags {
  const val SCROLLER = "scroller"
  const val ROW_ALWAYS_RELAY_CALLS = "row-always-relay-calls"
  const val ROW_CENSORSHIP_CIRCUMVENTION = "row-censorship-circumvention"
  const val ROW_SEALED_SENDER_STATUS_ICON = "row-sealed-sender-status-icon"
  const val ROW_SEALED_SENDER_FROM_ANYONE = "row-sealed-sender-from-anyone"
  const val ROW_SEALED_SENDER_LEARN_MORE = "row-sealed-sender-learn-more"
  const val ROW_AUTOMATIC_KEY_VERIFICATION = "row-automatic-key-verification"
}

/**
 * Call relaying, censorship circumvention, sealed sender, and automatic key verification.
 */
@Composable
fun AdvancedPrivacySettingsScreen(
  state: AdvancedPrivacySettingsState,
  onEvent: (AdvancedPrivacySettingsEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  LifecycleResumeEffect(Unit) {
    onEvent(AdvancedPrivacySettingsEvent.ScreenResumed)
    onPauseOrDispose {}
  }

  Scaffolds.Settings(
    title = stringResource(R.string.preferences__advanced),
    onNavigationClick = { onEvent(AdvancedPrivacySettingsEvent.NavigateBackClicked) },
    navigationIcon = SignalIcons.ArrowStart.imageVector,
    navigationContentDescription = stringResource(CoreUiR.string.CloseButton__content_description),
    modifier = modifier,
    expandableTitle = true
  ) { contentPadding ->
    AppSettingsLazyColumn(
      scaffoldPadding = contentPadding,
      modifier = Modifier.testTag(AdvancedPrivacySettingsTestTags.SCROLLER)
    ) {
      item {
        Rows.ToggleRow(
          checked = state.alwaysRelayCalls,
          text = stringResource(R.string.preferences_advanced__always_relay_calls),
          label = stringResource(R.string.preferences_advanced__relay_all_calls_through_the_signal_server_to_avoid_revealing_your_ip_address),
          onCheckChanged = { onEvent(AdvancedPrivacySettingsEvent.AlwaysRelayCallsToggled(it)) },
          modifier = Modifier.testTag(AdvancedPrivacySettingsTestTags.ROW_ALWAYS_RELAY_CALLS)
        )
      }

      item {
        Dividers.Default()
      }

      item {
        Texts.SectionHeader(text = stringResource(R.string.preferences_communication__category_censorship_circumvention))
      }

      item {
        Rows.ToggleRow(
          text = stringResource(R.string.preferences_communication__censorship_circumvention),
          label = stringResource(state.censorshipCircumventionState.summary()),
          checked = state.censorshipCircumventionEnabled,
          enabled = state.censorshipCircumventionState.available,
          onCheckChanged = { onEvent(AdvancedPrivacySettingsEvent.CensorshipCircumventionToggled(it)) },
          modifier = Modifier.testTag(AdvancedPrivacySettingsTestTags.ROW_CENSORSHIP_CIRCUMVENTION)
        )
      }

      item {
        Dividers.Default()
      }

      item {
        Texts.SectionHeader(text = stringResource(R.string.preferences_communication__category_sealed_sender))
      }

      item {
        SealedSenderStatusIconRow(
          checked = state.showSealedSenderStatusIcon,
          onEvent = onEvent
        )
      }

      if (state.isPrimaryDevice) {
        item {
          Rows.ToggleRow(
            checked = state.allowSealedSenderFromAnyone,
            text = stringResource(R.string.preferences_communication__sealed_sender_allow_from_anyone),
            label = stringResource(R.string.preferences_communication__sealed_sender_allow_from_anyone_description),
            onCheckChanged = { onEvent(AdvancedPrivacySettingsEvent.SealedSenderFromAnyoneToggled(it)) },
            modifier = Modifier.testTag(AdvancedPrivacySettingsTestTags.ROW_SEALED_SENDER_FROM_ANYONE)
          )
        }
      }

      item {
        Rows.TextRow(
          text = buildAnnotatedString {
            withLink(LinkAnnotation.Clickable("learn-more", linkInteractionListener = { onEvent(AdvancedPrivacySettingsEvent.SealedSenderLearnMoreClicked) })) {
              append(stringResource(CoreUiR.string.LearnMoreTextView_learn_more))
            }
          },
          modifier = Modifier.testTag(AdvancedPrivacySettingsTestTags.ROW_SEALED_SENDER_LEARN_MORE)
        )
      }

      item {
        Dividers.Default()
      }

      item {
        AutomaticKeyVerificationRow(
          checked = state.allowAutomaticKeyVerification,
          onEvent = onEvent
        )
      }
    }
  }
}

@Composable
private fun SealedSenderStatusIconRow(
  checked: Boolean,
  onEvent: (AdvancedPrivacySettingsEvent) -> Unit
) {
  val imageId = "sealed-sender-image"
  val text = buildAnnotatedString {
    append(stringResource(R.string.AdvancedPrivacySettingsFragment__show_status_icon))
    append(" ")
    appendInlineContent(imageId, "[image]")
  }

  val inlineContent = mapOf(
    imageId to InlineTextContent(
      placeholder = Placeholder(
        width = 20.sp,
        height = 15.sp,
        placeholderVerticalAlign = PlaceholderVerticalAlign.Center
      )
    ) {
      Icon(
        imageVector = ImageVector.vectorResource(CoreUiR.drawable.ic_unidentified_delivery),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  )

  Rows.ToggleRow(
    text = text,
    inlineContent = inlineContent,
    label = AnnotatedString(stringResource(R.string.AdvancedPrivacySettingsFragment__show_an_icon)),
    checked = checked,
    onCheckChanged = { onEvent(AdvancedPrivacySettingsEvent.SealedSenderStatusIconToggled(it)) },
    modifier = Modifier.testTag(AdvancedPrivacySettingsTestTags.ROW_SEALED_SENDER_STATUS_ICON)
  )
}

@Composable
private fun AutomaticKeyVerificationRow(
  checked: Boolean,
  onEvent: (AdvancedPrivacySettingsEvent) -> Unit
) {
  val label = buildAnnotatedString {
    append(stringResource(R.string.preferences_automatic_key_verification_body))
    append(" ")
    withLink(LinkAnnotation.Clickable("learn-more", linkInteractionListener = { onEvent(AdvancedPrivacySettingsEvent.AutomaticKeyVerificationLearnMoreClicked) })) {
      append(stringResource(CoreUiR.string.LearnMoreTextView_learn_more))
    }
  }

  Rows.ToggleRow(
    checked = checked,
    text = AnnotatedString(stringResource(R.string.preferences_automatic_key_verification)),
    label = label,
    onCheckChanged = { onEvent(AdvancedPrivacySettingsEvent.AutomaticKeyVerificationToggled(it)) },
    modifier = Modifier.testTag(AdvancedPrivacySettingsTestTags.ROW_AUTOMATIC_KEY_VERIFICATION)
  )
}

@StringRes
private fun CensorshipCircumventionState.summary(): Int {
  return when (this) {
    CensorshipCircumventionState.AVAILABLE -> R.string.preferences_communication__censorship_circumvention_if_enabled_signal_will_attempt_to_circumvent_censorship
    CensorshipCircumventionState.AVAILABLE_MANUALLY_DISABLED -> R.string.preferences_communication__censorship_circumvention_you_have_manually_disabled
    CensorshipCircumventionState.AVAILABLE_AUTOMATICALLY_ENABLED -> R.string.preferences_communication__censorship_circumvention_has_been_activated_based_on_your_accounts_phone_number
    CensorshipCircumventionState.UNAVAILABLE_CONNECTED -> R.string.preferences_communication__censorship_circumvention_is_not_necessary_you_are_already_connected
    CensorshipCircumventionState.UNAVAILABLE_NO_INTERNET -> R.string.preferences_communication__censorship_circumvention_can_only_be_activated_when_connected_to_the_internet
  }
}

@DayNightPreviews
@PreviewWrapper(SignalPreviewWrapper::class)
@Composable
private fun AdvancedPrivacySettingsScreenPreview() {
  AdvancedPrivacySettingsScreen(
    state = AdvancedPrivacySettingsState(
      censorshipCircumventionState = CensorshipCircumventionState.UNAVAILABLE_CONNECTED,
      isPrimaryDevice = true
    ),
    onEvent = {}
  )
}

@DayNightPreviews
@PreviewWrapper(SignalPreviewWrapper::class)
@Composable
private fun AdvancedPrivacySettingsScreenLinkedDevicePreview() {
  AdvancedPrivacySettingsScreen(
    state = AdvancedPrivacySettingsState(
      alwaysRelayCalls = true,
      censorshipCircumventionState = CensorshipCircumventionState.AVAILABLE_AUTOMATICALLY_ENABLED,
      censorshipCircumventionEnabled = true,
      showSealedSenderStatusIcon = true,
      allowAutomaticKeyVerification = true,
      isPrimaryDevice = false
    ),
    onEvent = {}
  )
}
