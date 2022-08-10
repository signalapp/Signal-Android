package org.signal.qrtest

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageView
import com.google.zxing.PlanarYUVLuminanceSource
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.kotlin.subscribeBy
import org.signal.core.util.ThreadUtil
import org.signal.qr.ImageProxyLuminanceSource
import org.signal.qr.QrProcessor
import org.signal.qr.QrScannerView

class QrMainActivity : AppCompatActivity() {
  @SuppressLint("NewApi", "SetTextI18n")
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    if (Build.VERSION.SDK_INT >= 23) {
      requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 1)
    }

    val scanner = findViewById<QrScannerView>(R.id.scanner)
    scanner.start(this)

    val qrText = findViewById<TextView>(R.id.text_qr_data)

    scanner.qrData
      .distinctUntilChanged()
      .observeOn(AndroidSchedulers.mainThread())
      .subscribeBy {
        qrText.text = it
        Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
      }

    val sourceView = findViewById<AppCompatImageView>(R.id.scanner_source)
    val qrSize = findViewById<TextView>(R.id.text_size)

    QrProcessor.listener = { source ->
      val bitmap = when (source) {
        is ImageProxyLuminanceSource -> Bitmap.createBitmap(source.render(), 0, source.width, source.width, source.height, Bitmap.Config.ARGB_8888)
        is PlanarYUVLuminanceSource -> Bitmap.createBitmap(source.renderThumbnail(), 0, source.thumbnailWidth, source.thumbnailWidth, source.thumbnailHeight, Bitmap.Config.ARGB_8888)
        else -> null
      }

      if (bitmap != null) {
        ThreadUtil.runOnMain {
          qrSize.text = "${bitmap.width} x ${bitmap.height}"
          sourceView.setImageBitmap(bitmap)
        }
      }
    }
  }
}
