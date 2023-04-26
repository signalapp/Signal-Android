package org.thoughtcrime.securesms.profiles.edit.pnp

import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import io.reactivex.rxjava3.kotlin.subscribeBy
import org.signal.core.util.concurrent.LifecycleDisposable
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.ViewBinderDelegate
import org.thoughtcrime.securesms.components.settings.DSLConfiguration
import org.thoughtcrime.securesms.components.settings.DSLSettingsFragment
import org.thoughtcrime.securesms.components.settings.DSLSettingsText
import org.thoughtcrime.securesms.components.settings.configure
import org.thoughtcrime.securesms.databinding.WhoCanSeeMyPhoneNumberFragmentBinding
import org.thoughtcrime.securesms.util.FeatureFlags
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter

/**
 * Allows the user to select who can see their phone number during registration.
 */
class WhoCanSeeMyPhoneNumberFragment : DSLSettingsFragment(
  titleId = R.string.WhoCanSeeMyPhoneNumberFragment__who_can_find_me_by_number,
  layoutId = R.layout.who_can_see_my_phone_number_fragment
) {

  companion object {
    /**
     * Components can listen to this result to know when the user hit the submit button.
     */
    const val REQUEST_KEY = "who_can_see_my_phone_number_key"
  }

  private val viewModel: WhoCanSeeMyPhoneNumberViewModel by viewModels()
  private val lifecycleDisposable = LifecycleDisposable()

  private val binding by ViewBinderDelegate(WhoCanSeeMyPhoneNumberFragmentBinding::bind)

  override fun bindAdapter(adapter: MappingAdapter) {
    require(FeatureFlags.phoneNumberPrivacy())

    lifecycleDisposable += viewModel.state.subscribe {
      adapter.submitList(getConfiguration(it).toMappingModelList())
    }

    binding.save.setOnClickListener {
      binding.save.isEnabled = false
      viewModel.onSave().subscribeBy(onComplete = {
        setFragmentResult(REQUEST_KEY, Bundle())
        findNavController().popBackStack()
      })
    }
  }

  private fun getConfiguration(state: WhoCanSeeMyPhoneNumberState): DSLConfiguration {
    return configure {
      radioPref(
        title = DSLSettingsText.from(R.string.PhoneNumberPrivacy_everyone),
        isChecked = state == WhoCanSeeMyPhoneNumberState.EVERYONE,
        onClick = { viewModel.onEveryoneCanSeeMyPhoneNumberSelected() }
      )

      radioPref(
        title = DSLSettingsText.from(R.string.PhoneNumberPrivacy_nobody),
        isChecked = state == WhoCanSeeMyPhoneNumberState.NOBODY,
        onClick = { viewModel.onNobodyCanSeeMyPhoneNumberSelected() }
      )

      textPref(
        title = DSLSettingsText.from(
          when (state) {
            WhoCanSeeMyPhoneNumberState.EVERYONE -> R.string.WhoCanSeeMyPhoneNumberFragment__anyone_who_has
            WhoCanSeeMyPhoneNumberState.NOBODY -> R.string.WhoCanSeeMyPhoneNumberFragment__nobody_on_signal
          },
          DSLSettingsText.TextAppearanceModifier(R.style.Signal_Text_BodyMedium),
          DSLSettingsText.ColorModifier(ContextCompat.getColor(requireContext(), R.color.signal_colorOnSurfaceVariant))
        )
      )
    }
  }
}
