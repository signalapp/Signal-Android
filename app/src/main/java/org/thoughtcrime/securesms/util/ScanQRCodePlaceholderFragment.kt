package org.thoughtcrime.securesms.util

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import network.loki.messenger.databinding.FragmentScanQrCodePlaceholderBinding

class ScanQRCodePlaceholderFragment: Fragment() {
    private lateinit var binding: FragmentScanQrCodePlaceholderBinding
    var delegate: ScanQRCodePlaceholderFragmentDelegate? = null

    override fun onCreateView(layoutInflater: LayoutInflater, viewGroup: ViewGroup?, bundle: Bundle?): View {
        binding = FragmentScanQrCodePlaceholderBinding.inflate(layoutInflater, viewGroup, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.grantCameraAccessButton.setOnClickListener { delegate?.requestCameraAccess() }
    }
}

interface ScanQRCodePlaceholderFragmentDelegate {

    fun requestCameraAccess()
}