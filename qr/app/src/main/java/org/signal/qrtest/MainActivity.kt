package org.signal.qrtest

import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.kotlin.subscribeBy
import org.signal.qr.QrScannerView

class MainActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    if (Build.VERSION.SDK_INT >= 23) {
      requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 1)
    }

    val scanner = findViewById<QrScannerView>(R.id.scanner)
    scanner.start(this)

    scanner.qrData
      .distinctUntilChanged()
      .observeOn(AndroidSchedulers.mainThread())
      .subscribeBy { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
  }
}
