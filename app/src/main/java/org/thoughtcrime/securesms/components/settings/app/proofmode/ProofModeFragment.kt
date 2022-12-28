package org.thoughtcrime.securesms.components.settings.app.proofmode

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.databinding.ProofModeFragmentBinding
import org.thoughtcrime.securesms.mediasend.ProofConstants
import org.thoughtcrime.securesms.mediasend.ProofModeUtil
import org.thoughtcrime.securesms.mediasend.setOnClickListenerWithThrottle

class ProofModeFragment : Fragment(R.layout.proof_mode_fragment) {
  private lateinit var binding: ProofModeFragmentBinding

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    binding = ProofModeFragmentBinding.inflate(inflater, container, false)

    setupView()

    return binding.root
  }

  private fun setupView() {
    binding.notarySwitch.isChecked = PreferenceManager.getDefaultSharedPreferences(requireContext()).getBoolean(ProofConstants.IS_PROOF_NOTARY_ENABLED_GLOBAL, true)
    binding.locationSwitch.isChecked = PreferenceManager.getDefaultSharedPreferences(requireContext()).getBoolean(ProofConstants.IS_PROOF_LOCATION_ENABLED_GLOBAL, true)
    binding.phoneSwitch.isChecked = PreferenceManager.getDefaultSharedPreferences(requireContext()).getBoolean(ProofConstants.IS_PROOF_PHONE_ENABLED_GLOBAL, true)
    binding.networkSwitch.isChecked = PreferenceManager.getDefaultSharedPreferences(requireContext()).getBoolean(ProofConstants.IS_PROOF_NETWORK_ENABLED_GLOBAL, true)
    binding.toolbar.setNavigationOnClickListener {
      findNavController().navigateUp()
    }
    binding.offLayout.setOnClickListenerWithThrottle {
      ProofModeUtil.setProofSettingsGlobal(
        context = requireContext(),
        proofDeviceIds = false,
        proofLocation = false,
        proofNetwork = false,
        proofNotary = false
      )
      binding.notarySwitch.isChecked = false
      binding.locationSwitch.isChecked = false
      binding.phoneSwitch.isChecked = false
      binding.networkSwitch.isChecked = false
    }
    binding.notaryLayout.setOnClickListenerWithThrottle {
      binding.notarySwitch.performClick()
    }
    binding.locationLayout.setOnClickListenerWithThrottle {
      binding.locationSwitch.performClick()
    }
    binding.phoneLayout.setOnClickListenerWithThrottle {
      binding.phoneSwitch.performClick()
    }
    binding.networkLayout.setOnClickListenerWithThrottle {
      binding.networkSwitch.performClick()
    }

    binding.notarySwitch.setOnCheckedChangeListener { _, isChecked ->
      ProofModeUtil.setProofSettingsGlobal(
        context = requireContext(),
        proofNotary = isChecked
      )
    }
    binding.locationSwitch.setOnCheckedChangeListener { _, isChecked ->
      ProofModeUtil.setProofSettingsGlobal(
        context = requireContext(),
        proofLocation = isChecked
      )
    }
    binding.phoneSwitch.setOnCheckedChangeListener { _, isChecked ->
      ProofModeUtil.setProofSettingsGlobal(
        context = requireContext(),
        proofDeviceIds = isChecked
      )
    }
    binding.networkSwitch.setOnCheckedChangeListener { _, isChecked ->
      ProofModeUtil.setProofSettingsGlobal(
        context = requireContext(),
        proofNetwork = isChecked
      )
    }
  }

}