package org.thoughtcrime.securesms.exporter.flow

import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.Fragment
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

  override fun getFragment(): Fragment {
    return NavHostFragment.create(R.navigation.sms_export)
  }

  companion object {
    @JvmStatic
    fun createIntent(context: Context): Intent = Intent(context, SmsExportActivity::class.java)
  }
}
