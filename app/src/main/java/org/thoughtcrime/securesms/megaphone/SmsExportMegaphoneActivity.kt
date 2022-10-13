package org.thoughtcrime.securesms.megaphone

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import org.thoughtcrime.securesms.PassphraseRequiredActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.databinding.SmsExportMegaphoneActivityBinding
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.exporter.flow.SmsExportActivity
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme
import org.thoughtcrime.securesms.util.DynamicTheme

class SmsExportMegaphoneActivity : PassphraseRequiredActivity() {

  companion object {
    const val REQUEST_CODE: Short = 5343
  }

  private val theme: DynamicTheme = DynamicNoActionBarTheme()
  private lateinit var binding: SmsExportMegaphoneActivityBinding
  private lateinit var smsExportLauncher: ActivityResultLauncher<Intent>

  override fun onPreCreate() {
    theme.onCreate(this)
  }

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    binding = SmsExportMegaphoneActivityBinding.inflate(layoutInflater)
    setContentView(binding.root)

    smsExportLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
      if (it.resultCode == Activity.RESULT_OK) {
        ApplicationDependencies.getMegaphoneRepository().markSeen(Megaphones.Event.SMS_EXPORT)
        setResult(Activity.RESULT_OK)
        finish()
      }
    }

    binding.toolbar.setNavigationOnClickListener { onBackPressed() }

    if (SignalStore.misc().smsExportPhase.isBlockingUi()) {
      binding.headline.setText(R.string.SmsExportMegaphoneActivity__signal_no_longer_supports_sms)
      binding.description.setText(R.string.SmsExportMegaphoneActivity__signal_has_removed_support_for_sending_sms_messages)
      binding.description.setLearnMoreVisible(false)
      binding.laterButton.setText(R.string.SmsExportMegaphoneActivity__learn_more)
      binding.laterButton.setOnClickListener {
        CommunicationActions.openBrowserLink(this, getString(R.string.sms_export_url))
      }
    } else {
      binding.headline.setText(R.string.SmsExportMegaphoneActivity__signal_will_no_longer_support_sms)
      binding.description.setText(R.string.SmsExportMegaphoneActivity__signal_will_soon_remove_support_for_sending_sms_messages)
      binding.description.setLearnMoreVisible(true)
      binding.description.setLink(getString(R.string.sms_export_url))
      binding.laterButton.setText(R.string.SmsExportMegaphoneActivity__remind_me_later)
      binding.laterButton.setOnClickListener {
        onBackPressed()
      }
    }

    binding.exportButton.setOnClickListener {
      smsExportLauncher.launch(SmsExportActivity.createIntent(this))
    }
  }

  override fun onBackPressed() {
    ApplicationDependencies.getMegaphoneRepository().markSeen(Megaphones.Event.SMS_EXPORT)
    setResult(Activity.RESULT_CANCELED)
    super.onBackPressed()
  }

  override fun onResume() {
    super.onResume()
    theme.onResume(this)
  }
}
