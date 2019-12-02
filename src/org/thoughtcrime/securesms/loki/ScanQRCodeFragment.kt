package org.thoughtcrime.securesms.loki

import android.content.res.Configuration
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.app.AppCompatActivity
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
    private var viewCreated = false
    var scanListener: ScanListener? = null
        set(value) { field = value; scanningThread.setScanListener(scanListener) }
    var mode: Mode = Mode.NewConversation
        set(value) { field = value; updateDescription(); }

    // region Types
    enum class Mode { NewConversation, LinkDevice }
    // endregion

    override fun onCreateView(layoutInflater: LayoutInflater, viewGroup: ViewGroup?, bundle: Bundle?): View? {
        return layoutInflater.inflate(R.layout.fragment_scan_qr_code, viewGroup, false)
    }

    override fun onViewCreated(view: View, bundle: Bundle?) {
        super.onViewCreated(view, bundle)
        viewCreated = true
        when (resources.configuration.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> overlayView.orientation = LinearLayout.HORIZONTAL
            else -> overlayView.orientation = LinearLayout.VERTICAL
        }
        updateDescription()
    }

    override fun onResume() {
        super.onResume()
        this.scanningThread.setScanListener(scanListener)
        this.cameraView.onResume()
        this.cameraView.setPreviewCallback(scanningThread)
        this.scanningThread.start()
        if (activity is AppCompatActivity) {
            val activity = activity as AppCompatActivity
            activity.supportActionBar?.setTitle(R.string.fragment_scan_qr_code_title)
        }
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

    fun updateDescription() {
        if (!viewCreated) { return }
        val text = when (mode) {
            Mode.NewConversation -> R.string.fragment_scan_qr_code_explanation_new_conversation
            Mode.LinkDevice -> R.string.fragment_scan_qr_code_explanation_link_device
        }
        descriptionTextView.setText(text)
    }
}