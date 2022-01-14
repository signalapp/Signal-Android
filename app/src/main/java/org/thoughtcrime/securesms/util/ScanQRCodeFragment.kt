package org.thoughtcrime.securesms.util

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import network.loki.messenger.databinding.FragmentScanQrCodeBinding
import org.thoughtcrime.securesms.qr.ScanListener
import org.thoughtcrime.securesms.qr.ScanningThread

class ScanQRCodeFragment : Fragment() {
    private lateinit var binding: FragmentScanQrCodeBinding
    private val scanningThread = ScanningThread()
    var scanListener: ScanListener? = null
        set(value) { field = value; scanningThread.setScanListener(scanListener) }
    var message: CharSequence = ""

    override fun onCreateView(layoutInflater: LayoutInflater, viewGroup: ViewGroup?, bundle: Bundle?): View {
        binding = FragmentScanQrCodeBinding.inflate(layoutInflater, viewGroup, false)
        return binding.root
    }

    override fun onViewCreated(view: View, bundle: Bundle?) {
        super.onViewCreated(view, bundle)
        when (resources.configuration.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> binding.overlayView.orientation = LinearLayout.HORIZONTAL
            else -> binding.overlayView.orientation = LinearLayout.VERTICAL
        }
        binding.messageTextView.text = message
    }

    override fun onResume() {
        super.onResume()
        binding.cameraView.onResume()
        binding.cameraView.setPreviewCallback(scanningThread)
        try {
            scanningThread.start()
        } catch (exception: Exception) {
            // Do nothing
        }
        scanningThread.setScanListener(scanListener)
    }

    override fun onConfigurationChanged(newConfiguration: Configuration) {
        super.onConfigurationChanged(newConfiguration)
        binding.cameraView.onPause()
        when (newConfiguration.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> binding.overlayView.orientation = LinearLayout.HORIZONTAL
            else -> binding.overlayView.orientation = LinearLayout.VERTICAL
        }
        binding.cameraView.onResume()
        binding.cameraView.setPreviewCallback(scanningThread)
    }

    override fun onPause() {
        super.onPause()
        this.binding.cameraView.onPause()
        this.scanningThread.stopScanning()
    }
}