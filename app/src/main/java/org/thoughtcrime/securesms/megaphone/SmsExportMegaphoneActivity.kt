package org.thoughtcrime.securesms.megaphone

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import org.thoughtcrime.securesms.PassphraseRequiredActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.databinding.SmsRemovalInformationFragmentBinding
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.exporter.flow.SmsExportActivity
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.DateUtils
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme
import org.thoughtcrime.securesms.util.DynamicTheme
import org.thoughtcrime.securesms.util.visible
import java.util.Locale

class SmsExportMegaphoneActivity : PassphraseRequiredActivity() {

  companion object {
    const val REQUEST_CODE: Short = 5343
  }

  private val theme: DynamicTheme = DynamicNoActionBarTheme()
  private lateinit var binding: SmsRemovalInformationFragmentBinding
  private lateinit var smsExportLauncher: ActivityResultLauncher<Intent>

  override fun onPreCreate() {
    theme.onCreate(this)
  }

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    binding = SmsRemovalInformationFragmentBinding.inflate(layoutInflater)
    setContentView(binding.root)

    smsExportLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
      if (it.resultCode == Activity.RESULT_OK) {
        ApplicationDependencies.getMegaphoneRepository().markSeen(Megaphones.Event.SMS_EXPORT)
        setResult(Activity.RESULT_OK)
        finish()
      }
    }

    binding.toolbar.setNavigationOnClickListener { onBackPressed() }

    binding.learnMoreButton.setOnClickListener {
      CommunicationActions.openBrowserLink(this, getString(R.string.sms_export_url))
    }

    if (SignalStore.misc().smsExportPhase.isBlockingUi()) {
      binding.headline.setText(R.string.SmsExportMegaphoneActivity__signal_no_longer_supports_sms)
      binding.laterButton.visible = false
      binding.bullet1Text.setText(R.string.SmsRemoval_info_bullet_1_phase_3)
    } else {
      val phase3Start = DateUtils.formatDateWithMonthAndDay(Locale.getDefault(), SignalStore.misc().smsPhase3Start)
      binding.bullet1Text.text = getString(R.string.SmsRemoval_info_bullet_1_s, phase3Start)

      binding.headline.setText(R.string.SmsExportMegaphoneActivity__signal_will_no_longer_support_sms)
      binding.laterButton.setOnClickListener {
        onBackPressed()
      }
    }

    binding.exportSmsButton.setOnClickListener {
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
