package org.signal.qr

import android.annotation.SuppressLint
import android.content.Context
import android.widget.FrameLayout
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import org.signal.qr.kitkat.QrCameraView
import org.signal.qr.kitkat.ScanListener
import org.signal.qr.kitkat.ScanningThread

/**
 * API19 version of QR scanning. Uses deprecated camera APIs.
 */
@SuppressLint("ViewConstructor")
internal class ScannerView19 constructor(
  context: Context,
  private val scanListener: ScanListener
) : FrameLayout(context), ScannerView {

  private var lifecycleOwner: LifecycleOwner? = null
  private var scanningThread: ScanningThread? = null
  private lateinit var cameraView: QrCameraView

  private val lifecycleObserver = object : DefaultLifecycleObserver {
    override fun onResume(owner: LifecycleOwner) {
      val scanningThread = ScanningThread()
      scanningThread.setScanListener(scanListener)
      cameraView.onResume()
      cameraView.setPreviewCallback(scanningThread)
      scanningThread.start()

      this@ScannerView19.scanningThread = scanningThread
    }

    override fun onPause(owner: LifecycleOwner) {
      cameraView.onPause()
      scanningThread?.stopScanning()
      scanningThread = null
    }
  }

  init {
    cameraView = QrCameraView(context)
    cameraView.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    addView(cameraView)
  }

  override fun start(lifecycleOwner: LifecycleOwner) {
    this.lifecycleOwner?.lifecycle?.removeObserver(lifecycleObserver)
    this.lifecycleOwner = lifecycleOwner
    lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
  }

  override fun toggleCamera() {
    cameraView.toggleCamera()
    lifecycleOwner?.let {
      lifecycleObserver.onPause(it)
      lifecycleObserver.onResume(it)
    }
  }
}
