package org.thoughtcrime.securesms.components.settings.app.internal.donor

import androidx.fragment.app.viewModels
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import org.signal.donations.StripeDeclineCode
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.DSLConfiguration
import org.thoughtcrime.securesms.components.settings.DSLSettingsFragment
import org.thoughtcrime.securesms.components.settings.DSLSettingsText
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.UnexpectedSubscriptionCancellation
import org.thoughtcrime.securesms.components.settings.configure
import org.thoughtcrime.securesms.util.LifecycleDisposable
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter

class DonorErrorConfigurationFragment : DSLSettingsFragment() {

  private val viewModel: DonorErrorConfigurationViewModel by viewModels()
  private val lifecycleDisposable = LifecycleDisposable()

  override fun bindAdapter(adapter: MappingAdapter) {
    lifecycleDisposable += viewModel.state.observeOn(AndroidSchedulers.mainThread()).subscribe { state ->
      adapter.submitList(getConfiguration(state).toMappingModelList())
    }
  }

  private fun getConfiguration(state: DonorErrorConfigurationState): DSLConfiguration {
    return configure {
      radioListPref(
        title = DSLSettingsText.from(R.string.preferences__internal_donor_error_expired_badge),
        selected = state.badges.indexOf(state.selectedBadge),
        listItems = state.badges.map { it.name }.toTypedArray(),
        onSelected = { viewModel.setSelectedBadge(it) }
      )

      radioListPref(
        title = DSLSettingsText.from(R.string.preferences__internal_donor_error_cancelation_reason),
        selected = UnexpectedSubscriptionCancellation.values().indexOf(state.selectedUnexpectedSubscriptionCancellation),
        listItems = UnexpectedSubscriptionCancellation.values().map { it.status }.toTypedArray(),
        onSelected = { viewModel.setSelectedUnexpectedSubscriptionCancellation(it) },
        isEnabled = state.selectedBadge == null || state.selectedBadge.isSubscription()
      )

      radioListPref(
        title = DSLSettingsText.from(R.string.preferences__internal_donor_error_charge_failure),
        selected = StripeDeclineCode.Code.values().indexOf(state.selectedStripeDeclineCode),
        listItems = StripeDeclineCode.Code.values().map { it.code }.toTypedArray(),
        onSelected = { viewModel.setStripeDeclineCode(it) },
        isEnabled = state.selectedBadge == null || state.selectedBadge.isSubscription()
      )

      primaryButton(
        text = DSLSettingsText.from(R.string.preferences__internal_donor_error_save_and_finish),
        onClick = {
          lifecycleDisposable += viewModel.save().subscribe { requireActivity().finish() }
        }
      )

      secondaryButtonNoOutline(
        text = DSLSettingsText.from(R.string.preferences__internal_donor_error_clear),
        onClick = {
          lifecycleDisposable += viewModel.clear().subscribe()
        }
      )
    }
  }
}
