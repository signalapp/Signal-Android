package org.signal.qr

import android.annotation.SuppressLint
import android.content.Context
import android.util.Size
import android.widget.FrameLayout
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import org.signal.core.util.logging.Log
import org.signal.qr.kitkat.ScanListener
import java.util.concurrent.Executors

/**
 * API21+ version of QR scanning view. Uses camerax APIs.
 */
@SuppressLint("ViewConstructor")
internal class ScannerView21 constructor(
  context: Context,
  private val listener: ScanListener
) : FrameLayout(context), ScannerView {

  private var lifecyleOwner: LifecycleOwner? = null
  private val analyzerExecutor = Executors.newSingleThreadExecutor()
  private var cameraProvider: ProcessCameraProvider? = null
  private var cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
  private var camera: Camera? = null
  private var previewView: PreviewView
  private val qrProcessor = QrProcessor()

  private val lifecycleObserver: DefaultLifecycleObserver = object : DefaultLifecycleObserver {
    override fun onDestroy(owner: LifecycleOwner) {
      cameraProvider = null
      camera = null
      analyzerExecutor.shutdown()
    }
  }

  init {
    previewView = PreviewView(context)
    previewView.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    addView(previewView)
  }

  override fun toggleCamera() {
    cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
      CameraSelector.DEFAULT_FRONT_CAMERA
    } else {
      CameraSelector.DEFAULT_BACK_CAMERA
    }

    lifecyleOwner?.let { start(it) }
  }

  override fun start(lifecycleOwner: LifecycleOwner) {
    this.lifecyleOwner?.lifecycle?.removeObserver(lifecycleObserver)
    this.lifecyleOwner = lifecycleOwner

    previewView.post {
      Log.i(TAG, "Starting")
      ProcessCameraProvider.getInstance(context).apply {
        addListener({
          try {
            onCameraProvider(lifecycleOwner, get())
          } catch (e: Exception) {
            Log.w(TAG, e)
          }
        }, ContextCompat.getMainExecutor(context))
      }
    }

    lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
  }

  private fun onCameraProvider(lifecycle: LifecycleOwner, cameraProvider: ProcessCameraProvider?) {
    if (cameraProvider == null) {
      Log.w(TAG, "Camera provider is null")
      return
    }

    Log.i(TAG, "Initializing use cases")

    val resolution = Size(480, 640)

    val preview = Preview.Builder()
      .setTargetResolution(resolution)
      .build()

    val imageAnalysis = ImageAnalysis.Builder()
      .setTargetResolution(resolution)
      .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
      .build()

    imageAnalysis.setAnalyzer(analyzerExecutor) { proxy ->
      proxy.use {
        val data: String? = qrProcessor.getScannedData(it)
        if (data != null) {
          listener.onQrDataFound(data)
        }
      }
    }

    cameraProvider.unbindAll()
    camera = cameraProvider.bindToLifecycle(lifecycle, cameraSelector, preview, imageAnalysis)

    preview.setSurfaceProvider(previewView.surfaceProvider)

    Log.d(TAG, "Preview:  ${preview.resolutionInfo}")
    Log.d(TAG, "Analysis: ${imageAnalysis.resolutionInfo}")

    this.cameraProvider = cameraProvider
  }

  companion object {
    private val TAG = Log.tag(ScannerView21::class.java)
  }
}
