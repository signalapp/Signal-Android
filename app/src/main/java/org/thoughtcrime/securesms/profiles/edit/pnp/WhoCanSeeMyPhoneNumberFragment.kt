package org.thoughtcrime.securesms.profiles.edit.pnp

import androidx.fragment.app.viewModels
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.DSLConfiguration
import org.thoughtcrime.securesms.components.settings.DSLSettingsFragment
import org.thoughtcrime.securesms.components.settings.DSLSettingsText
import org.thoughtcrime.securesms.components.settings.configure
import org.thoughtcrime.securesms.util.FeatureFlags
import org.thoughtcrime.securesms.util.LifecycleDisposable
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter

/**
 * Allows the user to select who can see their phone number during registration.
 */
class WhoCanSeeMyPhoneNumberFragment : DSLSettingsFragment(titleId = R.string.WhoCanSeeMyPhoneNumberFragment__who_can_find_me_by_number) {

  private val viewModel: WhoCanSeeMyPhoneNumberViewModel by viewModels()
  private val lifecycleDisposable = LifecycleDisposable()

  override fun bindAdapter(adapter: MappingAdapter) {
    require(FeatureFlags.phoneNumberPrivacy())

    lifecycleDisposable += viewModel.state.subscribe {
      adapter.submitList(getConfiguration(it).toMappingModelList())
    }
  }

  private fun getConfiguration(state: WhoCanSeeMyPhoneNumberState): DSLConfiguration {
    return configure {
      radioPref(
        title = DSLSettingsText.from(R.string.PhoneNumberPrivacy_everyone),
        summary = DSLSettingsText.from(R.string.WhoCanSeeMyPhoneNumberFragment__anyone_who_has),
        isChecked = state == WhoCanSeeMyPhoneNumberState.EVERYONE,
        onClick = { viewModel.onEveryoneCanSeeMyPhoneNumberSelected() }
      )

      radioPref(
        title = DSLSettingsText.from(R.string.PhoneNumberPrivacy_nobody),
        summary = DSLSettingsText.from(R.string.WhoCanSeeMyPhoneNumberFragment__nobody_on_signal),
        isChecked = state == WhoCanSeeMyPhoneNumberState.NOBODY,
        onClick = { viewModel.onNobodyCanSeeMyPhoneNumberSelected() }
      )
    }
  }
}
