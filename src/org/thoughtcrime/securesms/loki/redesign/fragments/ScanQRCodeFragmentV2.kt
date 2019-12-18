package org.thoughtcrime.securesms.loki.redesign.fragments

import android.content.res.Configuration
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import kotlinx.android.synthetic.main.fragment_scan_qr_code_v2.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.qr.ScanListener
import org.thoughtcrime.securesms.qr.ScanningThread

class ScanQRCodeFragmentV2 : Fragment() {
    private val scanningThread = ScanningThread()
    var scanListener: ScanListener? = null
        set(value) { field = value; scanningThread.setScanListener(scanListener) }
    var message: CharSequence = ""

    override fun onCreateView(layoutInflater: LayoutInflater, viewGroup: ViewGroup?, bundle: Bundle?): View? {
        return layoutInflater.inflate(R.layout.fragment_scan_qr_code_v2, viewGroup, false)
    }

    override fun onViewCreated(view: View, bundle: Bundle?) {
        super.onViewCreated(view, bundle)
        when (resources.configuration.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> overlayView.orientation = LinearLayout.HORIZONTAL
            else -> overlayView.orientation = LinearLayout.VERTICAL
        }
        messageTextView.text = message
    }

    override fun onResume() {
        super.onResume()
        scanningThread.setScanListener(scanListener)
        cameraView.onResume()
        cameraView.setPreviewCallback(scanningThread)
        scanningThread.start()
    }

    override fun onPause() {
        super.onPause()
        this.cameraView.onPause()
        this.scanningThread.stopScanning()
    }

    override fun onConfigurationChanged(newConfiguration: Configuration) {
        super.onConfigurationChanged(newConfiguration)
        this.cameraView.onPause()
        when (newConfiguration.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> overlayView.orientation = LinearLayout.HORIZONTAL
            else -> overlayView.orientation = LinearLayout.VERTICAL
        }
        cameraView.onResume()
        cameraView.setPreviewCallback(scanningThread)
    }
}