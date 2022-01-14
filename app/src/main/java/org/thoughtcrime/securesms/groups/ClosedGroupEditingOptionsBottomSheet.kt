package org.thoughtcrime.securesms.groups

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import network.loki.messenger.databinding.FragmentClosedGroupEditBottomSheetBinding

class ClosedGroupEditingOptionsBottomSheet : BottomSheetDialogFragment() {
    private lateinit var binding: FragmentClosedGroupEditBottomSheetBinding
    var onRemoveTapped: (() -> Unit)? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentClosedGroupEditBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.removeFromGroup.setOnClickListener { onRemoveTapped?.invoke() }
    }
}