package org.thoughtcrime.securesms.loki

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

class ScanQRCodeFragment : Fragment() {
    private val scanningThread = ScanningThread()
    var scanListener: ScanListener? = null
        set(value) { field = value; scanningThread.setScanListener(scanListener) }

    override fun onCreateView(layoutInflater: LayoutInflater, viewGroup: ViewGroup?, bundle: Bundle?): View? {
        return layoutInflater.inflate(R.layout.fragment_scan_qr_code, viewGroup, false)
    }

    override fun onViewCreated(view: View, bundle: Bundle?) {
        super.onViewCreated(view, bundle)
        when (resources.configuration.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> overlayView.orientation = LinearLayout.HORIZONTAL
            else -> overlayView.orientation = LinearLayout.VERTICAL
        }
    }

    override fun onResume() {
        super.onResume()
        this.scanningThread.setScanListener(scanListener)
        this.cameraView.onResume()
        this.cameraView.setPreviewCallback(scanningThread)
        this.scanningThread.start()
        val activity = activity as NewConversationActivity
        activity.supportActionBar!!.setTitle(R.string.fragment_scan_qr_code_title)
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