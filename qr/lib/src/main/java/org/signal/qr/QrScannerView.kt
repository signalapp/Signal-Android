package org.signal.qr

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.PublishSubject

/**
 * View for starting up a camera and scanning a QR-Code. Safe to use on an API version and
 * will delegate to legacy camera APIs or CameraX APIs when appropriate.
 *
 * QR-code data is emitted via [qrData] observable.
 */
class QrScannerView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), ScannerView {

  private val scannerView: ScannerView
  private val qrDataPublish: PublishSubject<String> = PublishSubject.create()

  val qrData: Observable<String> = qrDataPublish

  init {
    val scannerView: FrameLayout = if (Build.VERSION.SDK_INT >= 21) {
      ScannerView21(context) { qrDataPublish.onNext(it) }
    } else {
      ScannerView19(context) { qrDataPublish.onNext(it) }
    }

    scannerView.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    addView(scannerView)

    this.scannerView = (scannerView as ScannerView)
  }

  override fun start(lifecycleOwner: LifecycleOwner) {
    scannerView.start(lifecycleOwner)
    lifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
      override fun onDestroy(owner: LifecycleOwner) {
        qrDataPublish.onComplete()
      }
    })
  }
}
