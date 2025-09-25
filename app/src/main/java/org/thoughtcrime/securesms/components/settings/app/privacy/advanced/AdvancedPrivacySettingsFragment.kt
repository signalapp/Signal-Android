package org.thoughtcrime.securesms.components.settings.app.privacy.advanced

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.PreferenceManager
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalPreview
import org.signal.core.ui.compose.Texts
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.compose.rememberStatusBarColorNestedScrollModifier
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.viewModel

/**
 * Displays advanced privacy controls such as call relaying and
 * censorship circumvention to the user.
 */
class AdvancedPrivacySettingsFragment : ComposeFragment() {

  private val viewModel: AdvancedPrivacySettingsViewModel by viewModel {
    val repository = AdvancedPrivacySettingsRepository(requireContext())
    val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())

    AdvancedPrivacySettingsViewModel(
      preferences,
      repository
    )
  }

  private var networkReceiver: NetworkReceiver? = null

  override fun onResume() {
    super.onResume()
    viewModel.refresh()
    registerNetworkReceiver()
  }

  override fun onPause() {
    super.onPause()
    unregisterNetworkReceiver()
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    viewLifecycleOwner.lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.RESUMED) {
        viewModel.events.collect {
          if (it == AdvancedPrivacySettingsViewModel.Event.DISABLE_PUSH_FAILED) {
            Toast.makeText(
              requireContext(),
              R.string.ApplicationPreferencesActivity_error_connecting_to_server,
              Toast.LENGTH_LONG
            ).show()
          }
        }
      }
    }
  }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()

    AdvancedPrivacySettingsScreen(
      state = state,
      callbacks = remember { Callbacks() }
    )
  }

  @Suppress("DEPRECATION")
  private fun registerNetworkReceiver() {
    val context: Context? = context
    if (context != null && networkReceiver == null) {
      networkReceiver = NetworkReceiver()
      context.registerReceiver(networkReceiver, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))
    }
  }

  private fun unregisterNetworkReceiver() {
    val context: Context? = context
    if (context != null && networkReceiver != null) {
      context.unregisterReceiver(networkReceiver)
      networkReceiver = null
    }
  }

  private inner class NetworkReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
      viewModel.refresh()
    }
  }

  private inner class Callbacks : AdvancedPrivacySettingsCallbacks {
    override fun onNavigationClick() {
      requireActivity().onBackPressedDispatcher.onBackPressed()
    }

    override fun onAlwaysRelayCallsChanged(enabled: Boolean) {
      viewModel.setAlwaysRelayCalls(enabled)
    }

    override fun onCensorshipCircumventionChanged(enabled: Boolean) {
      viewModel.setCensorshipCircumventionEnabled(enabled)
    }

    override fun onShowStatusIconForSealedSenderChanged(enabled: Boolean) {
      viewModel.setShowStatusIconForSealedSender(enabled)
    }

    override fun onAllowSealedSenderFromAnyoneChanged(enabled: Boolean) {
      viewModel.setAllowSealedSenderFromAnyone(enabled)
    }

    override fun onSealedSenderLearnMoreClick() {
      CommunicationActions.openBrowserLink(
        requireContext(),
        getString(R.string.AdvancedPrivacySettingsFragment__sealed_sender_link)
      )
    }
  }
}

private interface AdvancedPrivacySettingsCallbacks {

  fun onNavigationClick() = Unit
  fun onAlwaysRelayCallsChanged(enabled: Boolean) = Unit
  fun onCensorshipCircumventionChanged(enabled: Boolean) = Unit
  fun onShowStatusIconForSealedSenderChanged(enabled: Boolean) = Unit
  fun onAllowSealedSenderFromAnyoneChanged(enabled: Boolean) = Unit
  fun onSealedSenderLearnMoreClick() = Unit

  object Empty : AdvancedPrivacySettingsCallbacks
}

@Composable
private fun AdvancedPrivacySettingsScreen(
  state: AdvancedPrivacySettingsState,
  callbacks: AdvancedPrivacySettingsCallbacks
) {
  Scaffolds.Settings(
    title = stringResource(R.string.preferences__advanced),
    onNavigationClick = callbacks::onNavigationClick,
    navigationIcon = ImageVector.vectorResource(R.drawable.symbol_arrow_start_24)
  ) { paddingValues ->
    LazyColumn(
      modifier = Modifier
        .padding(paddingValues)
        .then(rememberStatusBarColorNestedScrollModifier())
    ) {
      item {
        Rows.ToggleRow(
          checked = state.alwaysRelayCalls,
          text = stringResource(R.string.preferences_advanced__always_relay_calls),
          label = stringResource(R.string.preferences_advanced__relay_all_calls_through_the_signal_server_to_avoid_revealing_your_ip_address),
          onCheckChanged = callbacks::onAlwaysRelayCallsChanged
        )
      }

      item {
        Dividers.Default()
      }

      item {
        Texts.SectionHeader(text = stringResource(R.string.preferences_communication__category_censorship_circumvention))
      }

      item {
        val censorshipSummaryResId: Int = when (state.censorshipCircumventionState) {
          CensorshipCircumventionState.AVAILABLE -> R.string.preferences_communication__censorship_circumvention_if_enabled_signal_will_attempt_to_circumvent_censorship
          CensorshipCircumventionState.AVAILABLE_MANUALLY_DISABLED -> R.string.preferences_communication__censorship_circumvention_you_have_manually_disabled
          CensorshipCircumventionState.AVAILABLE_AUTOMATICALLY_ENABLED -> R.string.preferences_communication__censorship_circumvention_has_been_activated_based_on_your_accounts_phone_number
          CensorshipCircumventionState.UNAVAILABLE_CONNECTED -> R.string.preferences_communication__censorship_circumvention_is_not_necessary_you_are_already_connected
          CensorshipCircumventionState.UNAVAILABLE_NO_INTERNET -> R.string.preferences_communication__censorship_circumvention_can_only_be_activated_when_connected_to_the_internet
        }

        Rows.ToggleRow(
          text = stringResource(R.string.preferences_communication__censorship_circumvention),
          label = stringResource(censorshipSummaryResId),
          checked = state.censorshipCircumventionEnabled,
          enabled = state.censorshipCircumventionState.available,
          onCheckChanged = callbacks::onCensorshipCircumventionChanged
        )
      }

      item {
        Dividers.Default()
      }

      item {
        Texts.SectionHeader(
          text = stringResource(R.string.preferences_communication__category_sealed_sender)
        )
      }

      item {
        val imageId = "sealed-sender-image"
        val text = buildAnnotatedString {
          append(stringResource(R.string.AdvancedPrivacySettingsFragment__show_status_icon))
          append(" ")
          appendInlineContent(imageId, "[image]")
        }

        val inlineContentMap = mapOf(
          imageId to InlineTextContent(
            placeholder = Placeholder(
              width = 20.sp,
              height = 15.sp,
              placeholderVerticalAlign = PlaceholderVerticalAlign.Center
            )
          ) {
            Icon(
              imageVector = ImageVector.vectorResource(R.drawable.ic_unidentified_delivery),
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        )

        Rows.ToggleRow(
          text = text,
          inlineContent = inlineContentMap,
          label = AnnotatedString(stringResource(R.string.AdvancedPrivacySettingsFragment__show_an_icon)),
          checked = state.showSealedSenderStatusIcon,
          onCheckChanged = callbacks::onShowStatusIconForSealedSenderChanged
        )
      }

      item {
        Rows.ToggleRow(
          checked = state.allowSealedSenderFromAnyone,
          text = stringResource(R.string.preferences_communication__sealed_sender_allow_from_anyone),
          label = stringResource(R.string.preferences_communication__sealed_sender_allow_from_anyone_description),
          onCheckChanged = callbacks::onAllowSealedSenderFromAnyoneChanged
        )
      }

      item {
        val sealedSenderSummary = buildAnnotatedString {
          withLink(
            LinkAnnotation.Clickable("learn-more", linkInteractionListener = {
              callbacks.onSealedSenderLearnMoreClick()
            })
          ) {
            append(stringResource(R.string.LearnMoreTextView_learn_more))
          }
        }

        Rows.TextRow(
          text = sealedSenderSummary
        )
      }
    }
  }
}

@SignalPreview
@Composable
private fun AdvancedPrivacySettingsScreenPreview() {
  Previews.Preview {
    AdvancedPrivacySettingsScreen(
      state = AdvancedPrivacySettingsState(
        isPushEnabled = true,
        alwaysRelayCalls = false,
        censorshipCircumventionState = CensorshipCircumventionState.UNAVAILABLE_CONNECTED,
        censorshipCircumventionEnabled = false,
        showSealedSenderStatusIcon = false,
        allowSealedSenderFromAnyone = false,
        showProgressSpinner = false
      ),
      callbacks = AdvancedPrivacySettingsCallbacks.Empty
    )
  }
}
