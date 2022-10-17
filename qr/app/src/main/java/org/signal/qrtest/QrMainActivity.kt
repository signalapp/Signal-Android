package org.signal.qrtest

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageView
import com.google.zxing.PlanarYUVLuminanceSource
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.kotlin.subscribeBy
import org.signal.core.util.ThreadUtil
import org.signal.core.util.logging.AndroidLogger
import org.signal.core.util.logging.Log
import org.signal.qr.ImageProxyLuminanceSource
import org.signal.qr.QrProcessor
import org.signal.qr.QrScannerView

class QrMainActivity : AppCompatActivity() {

  private lateinit var text: EditText

  @SuppressLint("NewApi", "SetTextI18n")
  override fun onCreate(savedInstanceState: Bundle?) {
    Log.initialize(
      AndroidLogger(),
      object : Log.Logger() {
        override fun v(tag: String, message: String?, t: Throwable?, keepLonger: Boolean) {
          printlnFormatted('v', tag, message, t)
        }

        override fun d(tag: String, message: String?, t: Throwable?, keepLonger: Boolean) {
          printlnFormatted('d', tag, message, t)
        }

        override fun i(tag: String, message: String?, t: Throwable?, keepLonger: Boolean) {
          printlnFormatted('i', tag, message, t)
        }

        override fun w(tag: String, message: String?, t: Throwable?, keepLonger: Boolean) {
          printlnFormatted('w', tag, message, t)
        }

        override fun e(tag: String, message: String?, t: Throwable?, keepLonger: Boolean) {
          printlnFormatted('e', tag, message, t)
        }

        override fun flush() {}

        private fun printlnFormatted(level: Char, tag: String, message: String?, t: Throwable?) {
          ThreadUtil.runOnMain {
            val allText = text.text.toString() + "\n" + format(level, tag, message, t)
            text.setText(allText)
          }
        }

        private fun format(level: Char, tag: String, message: String?, t: Throwable?): String {
          return if (t != null) {
            String.format("%c[%s] %s %s:%s", level, tag, message, t.javaClass.simpleName, t.message)
          } else {
            String.format("%c[%s] %s", level, tag, message)
          }
        }
      }
    )

    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    text = findViewById(R.id.log)

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

    findViewById<View>(R.id.camera_switch).setOnClickListener {
      scanner.toggleCamera()
    }
  }
}
