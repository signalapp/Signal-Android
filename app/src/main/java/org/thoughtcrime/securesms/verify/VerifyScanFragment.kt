package org.thoughtcrime.securesms.verify

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.OneShotPreDrawListener
import androidx.fragment.app.Fragment
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import org.signal.core.util.concurrent.LifecycleDisposable
import org.signal.qr.QrScannerView
import org.signal.qr.kitkat.ScanListener
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.ShapeScrim
import org.thoughtcrime.securesms.mediasend.camerax.CameraXModelBlocklist
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.fragments.findListener

/**
 * QR Scanner for identity verification
 */
class VerifyScanFragment : Fragment() {
  private val lifecycleDisposable = LifecycleDisposable()

  private lateinit var cameraView: QrScannerView
  private lateinit var cameraScrim: ShapeScrim
  private lateinit var cameraMarks: ImageView

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

    cameraView.start(viewLifecycleOwner, CameraXModelBlocklist.isBlocklisted())

    lifecycleDisposable.bindTo(viewLifecycleOwner)

    lifecycleDisposable += cameraView
      .qrData
      .distinctUntilChanged()
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe { qrData: String ->
        findListener<ScanListener>()?.onQrDataFound(qrData)
      }
  }
}
