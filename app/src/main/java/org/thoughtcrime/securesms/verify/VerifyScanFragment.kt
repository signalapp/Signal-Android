package org.thoughtcrime.securesms.verify

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.OneShotPreDrawListener
import androidx.fragment.app.Fragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.ShapeScrim
import org.thoughtcrime.securesms.components.camera.CameraView
import org.thoughtcrime.securesms.qr.ScanListener
import org.thoughtcrime.securesms.qr.ScanningThread
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.fragments.requireListener

/**
 * QR Scanner for identity verification
 */
class VerifyScanFragment : Fragment() {
  private lateinit var cameraView: CameraView
  private lateinit var cameraScrim: ShapeScrim
  private lateinit var cameraMarks: ImageView
  private lateinit var scanningThread: ScanningThread
  private lateinit var scanListener: ScanListener

  override fun onAttach(context: Context) {
    super.onAttach(context)
    scanListener = requireListener()
  }

  override fun onCreateView(inflater: LayoutInflater, viewGroup: ViewGroup?, bundle: Bundle?): View? {
    return ViewUtil.inflate(inflater, viewGroup!!, R.layout.verify_scan_fragment)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    cameraView = view.findViewById(R.id.scanner)
    cameraScrim = view.findViewById(R.id.camera_scrim)
    cameraMarks = view.findViewById(R.id.camera_marks)
    OneShotPreDrawListener.add(cameraScrim) {
      val width = cameraScrim.scrimWidth
      val height = cameraScrim.scrimHeight
      ViewUtil.updateLayoutParams(cameraMarks, width, height)
    }
  }

  override fun onResume() {
    super.onResume()
    scanningThread = ScanningThread()
    scanningThread.setScanListener(scanListener)
    scanningThread.setCharacterSet("ISO-8859-1")
    cameraView.onResume()
    cameraView.setPreviewCallback(scanningThread)
    scanningThread.start()
  }

  override fun onPause() {
    super.onPause()
    cameraView.onPause()
    scanningThread.stopScanning()
  }

  override fun onConfigurationChanged(newConfiguration: Configuration) {
    super.onConfigurationChanged(newConfiguration)
    cameraView.onPause()
    cameraView.onResume()
    cameraView.setPreviewCallback(scanningThread)
  }
}
