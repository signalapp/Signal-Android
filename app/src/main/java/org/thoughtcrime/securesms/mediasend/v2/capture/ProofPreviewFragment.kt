package org.thoughtcrime.securesms.mediasend.v2.capture

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.databinding.ProofModePreviewBinding

class ProofPreviewFragment : Fragment(R.layout.proof_mode_preview) {

  private lateinit var binding: ProofModePreviewBinding


  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    binding = ProofModePreviewBinding.inflate(inflater, container, false)

    binding.cancelButton.setOnClickListener {
      findNavController().navigateUp()
    }

    binding.confirmButton.setOnClickListener {
      findNavController().navigateUp()
    }

    return binding.root
  }
}