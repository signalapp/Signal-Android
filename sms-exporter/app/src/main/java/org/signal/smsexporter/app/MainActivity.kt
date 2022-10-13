package org.signal.smsexporter.app

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.smsexporter.DefaultSmsHelper
import org.signal.smsexporter.ReleaseSmsAppFailure
import org.signal.smsexporter.SmsExportProgress
import org.signal.smsexporter.SmsExportService

class MainActivity : AppCompatActivity(R.layout.main_activity) {

  private lateinit var exportSmsButton: MaterialButton
  private lateinit var setAsDefaultSmsButton: MaterialButton
  private lateinit var clearDefaultSmsButton: MaterialButton
  private lateinit var exportStatus: TextView
  private lateinit var exportProgress: LinearProgressIndicator
  private val disposables = CompositeDisposable()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    exportSmsButton = findViewById(R.id.export_sms)
    setAsDefaultSmsButton = findViewById(R.id.set_as_default_sms)
    clearDefaultSmsButton = findViewById(R.id.clear_default_sms)
    exportStatus = findViewById(R.id.export_status)
    exportProgress = findViewById(R.id.export_progress)

    disposables += SmsExportService.progressState.onBackpressureLatest().subscribeOn(Schedulers.computation()).observeOn(AndroidSchedulers.mainThread()).subscribe {
      when (it) {
        is SmsExportProgress.Done -> {
          exportStatus.text = "Done"
          exportProgress.isVisible = true
        }
        is SmsExportProgress.InProgress -> {
          exportStatus.text = "$it"
          exportProgress.isVisible = true
          exportProgress.progress = it.progress
          exportProgress.max = it.total
        }
        SmsExportProgress.Init -> {
          exportStatus.text = "Init"
          exportProgress.isVisible = false
        }
        SmsExportProgress.Starting -> {
          exportStatus.text = "Starting"
          exportProgress.isVisible = true
        }
      }
    }

    setAsDefaultSmsButton.setOnClickListener {
      DefaultSmsHelper.becomeDefaultSms(this).either(
        onFailure = { onAppIsIneligableForDefaultSmsSelection() },
        onSuccess = this::onStartActivityForDefaultSmsSelection
      )
    }

    clearDefaultSmsButton.setOnClickListener {
      DefaultSmsHelper.releaseDefaultSms(this).either(
        onFailure = {
          when (it) {
            ReleaseSmsAppFailure.APP_IS_INELIGIBLE_TO_RELEASE_SMS_SELECTION -> onAppIsIneligibleForReleaseSmsSelection()
            ReleaseSmsAppFailure.NO_METHOD_TO_RELEASE_SMS_AVIALABLE -> onNoMethodToReleaseSmsAvailable()
          }
        },
        onSuccess = this::onStartActivityForReleaseSmsSelection
      )
    }

    exportSmsButton.setOnClickListener {
      exportSmsButton.isEnabled = false
      ContextCompat.startForegroundService(this, Intent(this, TestSmsExportService::class.java))
    }

    presentButtonState()
  }

  override fun onResume() {
    super.onResume()
    presentButtonState()
  }

  override fun onDestroy() {
    super.onDestroy()
    disposables.clear()
  }

  private fun presentButtonState() {
    setAsDefaultSmsButton.isVisible = !DefaultSmsHelper.isDefaultSms(this)
    clearDefaultSmsButton.isVisible = DefaultSmsHelper.isDefaultSms(this)
    exportSmsButton.isVisible = DefaultSmsHelper.isDefaultSms(this)
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    when (requestCode) {
      1 -> presentButtonState()
      2 -> presentButtonState()
      else -> super.onActivityResult(requestCode, resultCode, data)
    }
  }

  private fun onStartActivityForDefaultSmsSelection(intent: Intent) {
    startActivityForResult(intent, 1)
  }

  private fun onAppIsIneligableForDefaultSmsSelection() {
    if (DefaultSmsHelper.isDefaultSms(this)) {
      Toast.makeText(this, "Already the SMS manager.", Toast.LENGTH_SHORT).show()
    } else {
      Toast.makeText(this, "Cannot be SMS manager.", Toast.LENGTH_SHORT).show()
    }
  }

  private fun onStartActivityForReleaseSmsSelection(intent: Intent) {
    startActivityForResult(intent, 2)
  }

  private fun onAppIsIneligibleForReleaseSmsSelection() {
    if (!DefaultSmsHelper.isDefaultSms(this)) {
      Toast.makeText(this, "Already not the SMS manager.", Toast.LENGTH_SHORT).show()
    } else {
      Toast.makeText(this, "Cannot be SMS manager.", Toast.LENGTH_SHORT).show()
    }
  }

  private fun onNoMethodToReleaseSmsAvailable() {
    Toast.makeText(this, "Cannot automatically release sms. Display manual instructions.", Toast.LENGTH_SHORT).show()
  }
}
