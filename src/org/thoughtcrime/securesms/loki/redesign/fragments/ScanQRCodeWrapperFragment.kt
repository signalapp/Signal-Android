package org.thoughtcrime.securesms.loki.redesign.fragments

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.tbruyelle.rxpermissions2.RxPermissions
import network.loki.messenger.R
import org.thoughtcrime.securesms.qr.ScanListener

class ScanQRCodeWrapperFragment : Fragment(), ScanQRCodePlaceholderFragmentDelegate, ScanListener {
    var delegate: ScanQRCodeWrapperFragmentDelegate? = null
    var message: CharSequence = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_scan_qr_code_wrapper, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        update()
    }

    private fun update() {
        val fragment: Fragment
        if (ContextCompat.checkSelfPermission(activity!!, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            val scanQRCodeFragment = ScanQRCodeFragment()
            scanQRCodeFragment.scanListener = this
            scanQRCodeFragment.message = message
            fragment = scanQRCodeFragment
        } else {
            val scanQRCodePlaceholderFragment = ScanQRCodePlaceholderFragment()
            scanQRCodePlaceholderFragment.delegate = this
            fragment = scanQRCodePlaceholderFragment
        }
        val transaction = childFragmentManager.beginTransaction()
        transaction.replace(R.id.fragmentContainer, fragment)
        transaction.commit()
    }

    override fun requestCameraAccess() {
        @SuppressWarnings("unused")
        val unused = RxPermissions(this).request(Manifest.permission.CAMERA).subscribe { isGranted ->
            if (isGranted) {
                update()
            }
        }
    }

    override fun onQrDataFound(string: String) {
        activity?.runOnUiThread {
            delegate?.handleQRCodeScanned(string)
        }
    }
}

interface ScanQRCodeWrapperFragmentDelegate {

    fun handleQRCodeScanned(string: String)
}