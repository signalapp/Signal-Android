package org.thoughtcrime.securesms.loki.fragments

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.fragment_scan_qr_code_placeholder.*
import network.loki.messenger.R

class ScanQRCodePlaceholderFragment: Fragment() {
    var delegate: ScanQRCodePlaceholderFragmentDelegate? = null

    override fun onCreateView(layoutInflater: LayoutInflater, viewGroup: ViewGroup?, bundle: Bundle?): View? {
        return layoutInflater.inflate(R.layout.fragment_scan_qr_code_placeholder, viewGroup, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        grantCameraAccessButton.setOnClickListener { delegate?.requestCameraAccess() }
    }
}

interface ScanQRCodePlaceholderFragmentDelegate {

    fun requestCameraAccess()
}