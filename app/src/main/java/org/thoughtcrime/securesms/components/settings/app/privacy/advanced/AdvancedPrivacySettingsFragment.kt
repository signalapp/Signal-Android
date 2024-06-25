package org.thoughtcrime.securesms.components.settings.app.privacy.advanced

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.net.ConnectivityManager
import android.text.SpannableStringBuilder
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.SignalProgressDialog
import org.thoughtcrime.securesms.components.settings.DSLConfiguration
import org.thoughtcrime.securesms.components.settings.DSLSettingsFragment
import org.thoughtcrime.securesms.components.settings.DSLSettingsText
import org.thoughtcrime.securesms.components.settings.configure
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.phonenumbers.PhoneNumberFormatter
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.SpanUtil
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter

class AdvancedPrivacySettingsFragment : DSLSettingsFragment(R.string.preferences__advanced) {

  private lateinit var viewModel: AdvancedPrivacySettingsViewModel

  private var networkReceiver: NetworkReceiver? = null

  private val sealedSenderSummary: CharSequence by lazy {
    SpanUtil.learnMore(
      requireContext(),
      ContextCompat.getColor(requireContext(), R.color.signal_text_primary)
    ) {
      CommunicationActions.openBrowserLink(
        requireContext(),
        getString(R.string.AdvancedPrivacySettingsFragment__sealed_sender_link)
      )
    }
  }

  var progressDialog: SignalProgressDialog? = null

  val statusIcon: CharSequence by lazy {
    val unidentifiedDeliveryIcon = requireNotNull(
      ContextCompat.getDrawable(
        requireContext(),
        R.drawable.ic_unidentified_delivery
      )
    )
    unidentifiedDeliveryIcon.setBounds(0, 0, ViewUtil.dpToPx(20), ViewUtil.dpToPx(20))
    val iconTint = ContextCompat.getColor(requireContext(), R.color.signal_text_primary_dialog)
    unidentifiedDeliveryIcon.colorFilter = PorterDuffColorFilter(iconTint, PorterDuff.Mode.SRC_IN)

    SpanUtil.buildImageSpan(unidentifiedDeliveryIcon)
  }

  override fun onResume() {
    super.onResume()
    viewModel.refresh()
    registerNetworkReceiver()
  }

  override fun onPause() {
    super.onPause()
    unregisterNetworkReceiver()
  }

  override fun bindAdapter(adapter: MappingAdapter) {
    val repository = AdvancedPrivacySettingsRepository(requireContext())
    val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
    val factory = AdvancedPrivacySettingsViewModel.Factory(preferences, repository)

    viewModel = ViewModelProvider(this, factory)[AdvancedPrivacySettingsViewModel::class.java]

    viewModel.state.observe(viewLifecycleOwner) {
      if (it.showProgressSpinner) {
        if (progressDialog?.isShowing == false) {
          progressDialog = SignalProgressDialog.show(requireContext(), null, null, true)
        }
      } else {
        progressDialog?.hide()
      }

      adapter.submitList(getConfiguration(it).toMappingModelList())
    }

    viewModel.events.observe(viewLifecycleOwner) {
      if (it == AdvancedPrivacySettingsViewModel.Event.DISABLE_PUSH_FAILED) {
        Toast.makeText(
          requireContext(),
          R.string.ApplicationPreferencesActivity_error_connecting_to_server,
          Toast.LENGTH_LONG
        ).show()
      }
    }
  }

  private fun getConfiguration(state: AdvancedPrivacySettingsState): DSLConfiguration {
    return configure {
      switchPref(
        title = DSLSettingsText.from(R.string.preferences_advanced__always_relay_calls),
        summary = DSLSettingsText.from(R.string.preferences_advanced__relay_all_calls_through_the_signal_server_to_avoid_revealing_your_ip_address),
        isChecked = state.alwaysRelayCalls
      ) {
        viewModel.setAlwaysRelayCalls(!state.alwaysRelayCalls)
      }

      dividerPref()

      sectionHeaderPref(R.string.preferences_communication__category_censorship_circumvention)

      val censorshipSummaryResId: Int = when (state.censorshipCircumventionState) {
        CensorshipCircumventionState.AVAILABLE -> R.string.preferences_communication__censorship_circumvention_if_enabled_signal_will_attempt_to_circumvent_censorship
        CensorshipCircumventionState.AVAILABLE_MANUALLY_DISABLED -> R.string.preferences_communication__censorship_circumvention_you_have_manually_disabled
        CensorshipCircumventionState.AVAILABLE_AUTOMATICALLY_ENABLED -> R.string.preferences_communication__censorship_circumvention_has_been_activated_based_on_your_accounts_phone_number
        CensorshipCircumventionState.UNAVAILABLE_CONNECTED -> R.string.preferences_communication__censorship_circumvention_is_not_necessary_you_are_already_connected
        CensorshipCircumventionState.UNAVAILABLE_NO_INTERNET -> R.string.preferences_communication__censorship_circumvention_can_only_be_activated_when_connected_to_the_internet
      }

      switchPref(
        title = DSLSettingsText.from(R.string.preferences_communication__censorship_circumvention),
        summary = DSLSettingsText.from(censorshipSummaryResId),
        isChecked = state.censorshipCircumventionEnabled,
        isEnabled = state.censorshipCircumventionState.available,
        onClick = {
          viewModel.setCensorshipCircumventionEnabled(!state.censorshipCircumventionEnabled)
        }
      )

      dividerPref()

      sectionHeaderPref(R.string.preferences_communication__category_sealed_sender)

      switchPref(
        title = DSLSettingsText.from(
          SpannableStringBuilder(getString(R.string.AdvancedPrivacySettingsFragment__show_status_icon))
            .append(" ")
            .append(statusIcon)
        ),
        summary = DSLSettingsText.from(R.string.AdvancedPrivacySettingsFragment__show_an_icon),
        isChecked = state.showSealedSenderStatusIcon
      ) {
        viewModel.setShowStatusIconForSealedSender(!state.showSealedSenderStatusIcon)
      }

      switchPref(
        title = DSLSettingsText.from(R.string.preferences_communication__sealed_sender_allow_from_anyone),
        summary = DSLSettingsText.from(R.string.preferences_communication__sealed_sender_allow_from_anyone_description),
        isChecked = state.allowSealedSenderFromAnyone
      ) {
        viewModel.setAllowSealedSenderFromAnyone(!state.allowSealedSenderFromAnyone)
      }

      textPref(
        summary = DSLSettingsText.from(sealedSenderSummary)
      )
    }
  }

  private fun getPushToggleSummary(isPushEnabled: Boolean): String {
    return if (isPushEnabled) {
      PhoneNumberFormatter.prettyPrint(SignalStore.account.e164!!)
    } else {
      getString(R.string.preferences__free_private_messages_and_calls)
    }
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
}
