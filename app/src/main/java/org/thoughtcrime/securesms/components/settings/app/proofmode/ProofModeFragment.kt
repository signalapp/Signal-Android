package org.thoughtcrime.securesms.components.settings.app.proofmode

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.databinding.ProofModeFragmentBinding

class ProofModeFragment : Fragment(R.layout.proof_mode_fragment) {
  private lateinit var binding: ProofModeFragmentBinding


  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    binding = ProofModeFragmentBinding.inflate(inflater, container, false)


    return binding.root
  }

}