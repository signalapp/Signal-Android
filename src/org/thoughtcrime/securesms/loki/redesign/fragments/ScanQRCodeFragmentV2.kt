package org.thoughtcrime.securesms.loki.redesign.fragments

import android.content.res.Configuration
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import kotlinx.android.synthetic.main.fragment_scan_qr_code.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.qr.ScanListener
import org.thoughtcrime.securesms.qr.ScanningThread

class ScanQRCodeFragmentV2 : Fragment() {
    private val scanningThread = ScanningThread()
    private var viewCreated = false
    var scanListener: ScanListener? = null
        set(value) { field = value; scanningThread.setScanListener(scanListener) }

    override fun onCreateView(layoutInflater: LayoutInflater, viewGroup: ViewGroup?, bundle: Bundle?): View? {
        return layoutInflater.inflate(R.layout.fragment_scan_qr_code_v2, viewGroup, false)
    }

    override fun onViewCreated(view: View, bundle: Bundle?) {
        super.onViewCreated(view, bundle)
        viewCreated = true
        when (resources.configuration.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> overlayView.orientation = LinearLayout.HORIZONTAL
            else -> overlayView.orientation = LinearLayout.VERTICAL
        }
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