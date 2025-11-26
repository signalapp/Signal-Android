package org.thoughtcrime.securesms.profiles.edit.pnp

import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.ViewBinderDelegate
import org.thoughtcrime.securesms.components.settings.DSLConfiguration
import org.thoughtcrime.securesms.components.settings.DSLSettingsFragment
import org.thoughtcrime.securesms.components.settings.DSLSettingsText
import org.thoughtcrime.securesms.components.settings.configure
import org.thoughtcrime.securesms.databinding.WhoCanFindMeByPhoneNumberFragmentBinding
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter

/**
 * Allows the user to select who can see their phone number during registration.
 */
class WhoCanFindMeByPhoneNumberFragment : DSLSettingsFragment(
  titleId = R.string.WhoCanSeeMyPhoneNumberFragment__who_can_find_me_by_number,
  layoutId = R.layout.who_can_find_me_by_phone_number_fragment
) {

  companion object {
    /**
     * Components can listen to this result to know when the user hit the submit button.
     */
    const val REQUEST_KEY = "who_can_see_my_phone_number_key"
  }

  private val viewModel: WhoCanFindMeByPhoneNumberViewModel by viewModels()

  private val binding by ViewBinderDelegate(WhoCanFindMeByPhoneNumberFragmentBinding::bind)

  override fun bindAdapter(adapter: MappingAdapter) {
    viewLifecycleOwner.lifecycleScope.launch {
      viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.state.collect { state ->
          adapter.submitList(getConfiguration(state).toMappingModelList())
        }
      }
    }

    binding.save.setOnClickListener {
      binding.save.isEnabled = false
      viewLifecycleOwner.lifecycleScope.launch {
        viewModel.onSave()
        setFragmentResult(REQUEST_KEY, Bundle())
        findNavController().popBackStack()
      }
    }
  }

  private fun getConfiguration(state: WhoCanFindMeByPhoneNumberState): DSLConfiguration {
    return configure {
      radioPref(
        title = DSLSettingsText.from(R.string.PhoneNumberPrivacy_everyone),
        isChecked = state == WhoCanFindMeByPhoneNumberState.EVERYONE,
        onClick = { viewModel.onEveryoneCanFindMeByPhoneNumberSelected() }
      )

      radioPref(
        title = DSLSettingsText.from(R.string.PhoneNumberPrivacy_nobody),
        isChecked = state == WhoCanFindMeByPhoneNumberState.NOBODY,
        onClick = this@WhoCanFindMeByPhoneNumberFragment::onNobodyCanFindMeByNumberClicked
      )

      textPref(
        title = DSLSettingsText.from(
          when (state) {
            WhoCanFindMeByPhoneNumberState.EVERYONE -> R.string.WhoCanSeeMyPhoneNumberFragment__anyone_who_has_your
            WhoCanFindMeByPhoneNumberState.NOBODY -> R.string.WhoCanSeeMyPhoneNumberFragment__nobody_will_be_able
          },
          DSLSettingsText.TextAppearanceModifier(R.style.Signal_Text_BodyMedium),
          DSLSettingsText.ColorModifier(ContextCompat.getColor(requireContext(), R.color.signal_colorOnSurfaceVariant))
        )
      )
    }
  }

  private fun onNobodyCanFindMeByNumberClicked() {
    MaterialAlertDialogBuilder(requireContext())
      .setTitle(R.string.PhoneNumberPrivacySettingsFragment__nobody_can_find_me_warning_title)
      .setMessage(getString(R.string.PhoneNumberPrivacySettingsFragment__nobody_can_find_me_warning_message))
      .setNegativeButton(getString(R.string.PhoneNumberPrivacySettingsFragment__cancel), null)
      .setPositiveButton(android.R.string.ok) { _, _ -> viewModel.onNobodyCanFindMeByPhoneNumberSelected() }
      .show()
  }
}
