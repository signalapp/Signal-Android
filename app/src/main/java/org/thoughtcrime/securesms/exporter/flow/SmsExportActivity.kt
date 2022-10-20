package org.thoughtcrime.securesms.exporter.flow

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.FragmentWrapperActivity
import org.thoughtcrime.securesms.notifications.NotificationIds
import org.thoughtcrime.securesms.util.WindowUtil

class SmsExportActivity : FragmentWrapperActivity() {

  override fun onResume() {
    super.onResume()
    WindowUtil.setLightStatusBarFromTheme(this)
    NotificationManagerCompat.from(this).cancel(NotificationIds.SMS_EXPORT_COMPLETE)
  }

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    super.onCreate(savedInstanceState, ready)
    onBackPressedDispatcher.addCallback(this, OnBackPressed())
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
    @JvmStatic
    fun createIntent(context: Context): Intent = Intent(context, SmsExportActivity::class.java)
  }
}
