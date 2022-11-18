package org.thoughtcrime.securesms.exporter.flow

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.FragmentWrapperActivity
import org.thoughtcrime.securesms.notifications.NotificationIds
import org.thoughtcrime.securesms.util.WindowUtil

class SmsExportActivity : FragmentWrapperActivity() {

  private lateinit var viewModel: SmsExportViewModel

  override fun onResume() {
    super.onResume()
    WindowUtil.setLightStatusBarFromTheme(this)
    NotificationManagerCompat.from(this).cancel(NotificationIds.SMS_EXPORT_COMPLETE)
  }

  @Suppress("ReplaceGetOrSet")
  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    super.onCreate(savedInstanceState, ready)
    onBackPressedDispatcher.addCallback(this, OnBackPressed())

    val factory = SmsExportViewModel.Factory(intent.getBooleanExtra(IS_RE_EXPORT, false))
    viewModel = ViewModelProvider(this, factory).get(SmsExportViewModel::class.java)
  }

  override fun getFragment(): Fragment {
    return NavHostFragment.create(R.navigation.sms_export)
  }

  private inner class OnBackPressed : OnBackPressedCallback(true) {
    override fun handleOnBackPressed() {
      if (!findNavController(R.id.fragment_container).popBackStack()) {
        finish()
      }
    }
  }

  companion object {
    const val IS_RE_EXPORT = "is_re_export"

    @JvmOverloads
    @JvmStatic
    fun createIntent(context: Context, isReExport: Boolean = false): Intent {
      return Intent(context, SmsExportActivity::class.java).apply {
        putExtra(IS_RE_EXPORT, isReExport)
      }
    }
  }
}
