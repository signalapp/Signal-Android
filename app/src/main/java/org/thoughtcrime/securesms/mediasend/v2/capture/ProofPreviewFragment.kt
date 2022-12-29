package org.thoughtcrime.securesms.mediasend.v2.capture

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.databinding.ProofModePreviewBinding
import org.thoughtcrime.securesms.mediasend.ProofConstants
import org.thoughtcrime.securesms.mediasend.ProofModeUtil
import org.thoughtcrime.securesms.mediasend.setOnClickListenerWithThrottle

class ProofPreviewFragment : Fragment(R.layout.proof_mode_preview) {

  private lateinit var binding: ProofModePreviewBinding


  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    binding = ProofModePreviewBinding.inflate(inflater, container, false)

    setupView()

    return binding.root
  }

  private fun setupView() {
    binding.cancelButton.setOnClickListener {
      findNavController().navigateUp()
    }

    val notaryGlobal = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(ProofConstants.IS_PROOF_NOTARY_ENABLED_GLOBAL, true)
    val locationGlobal = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(ProofConstants.IS_PROOF_LOCATION_ENABLED_GLOBAL, true)
    val phoneGlobal = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(ProofConstants.IS_PROOF_PHONE_ENABLED_GLOBAL, true)
    val networkGlobal = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(ProofConstants.IS_PROOF_NETWORK_ENABLED_GLOBAL, true)
    val notaryLocal = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(ProofConstants.IS_PROOF_NOTARY_ENABLED_LOCAL, true)
    val locationLocal = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(ProofConstants.IS_PROOF_LOCATION_ENABLED_LOCAL, true)
    val phoneLocal = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(ProofConstants.IS_PROOF_PHONE_ENABLED_LOCAL, true)
    val networkLocal = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(ProofConstants.IS_PROOF_NETWORK_ENABLED_LOCAL, true)
    val resultNotary = if (notaryGlobal == notaryLocal) notaryGlobal else notaryLocal
    val resultLocation = if (locationGlobal == locationLocal) locationGlobal else locationLocal
    val resultPhone = if (phoneGlobal == phoneLocal) phoneGlobal else phoneLocal
    val resultNetwork = if (networkGlobal == networkLocal) networkGlobal else networkLocal

    binding.notarySwitch.isChecked = resultNotary
    binding.locationSwitch.isChecked = resultLocation
    binding.phoneSwitch.isChecked = resultPhone
    binding.networkSwitch.isChecked = resultNetwork

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

    binding.confirmButton.setOnClickListenerWithThrottle {
      ProofModeUtil.setProofSettingsLocal(
        context = requireContext(),
        proofNotary = binding.notarySwitch.isChecked,
        proofLocation = binding.locationSwitch.isChecked,
        proofDeviceIds = binding.phoneSwitch.isChecked,
        proofNetwork = binding.networkSwitch.isChecked
      )
      findNavController().navigateUp()
    }
  }
}