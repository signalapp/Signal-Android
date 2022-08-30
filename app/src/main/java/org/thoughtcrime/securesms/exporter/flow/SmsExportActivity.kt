package org.thoughtcrime.securesms.exporter.flow

import android.content.Context
import android.content.Intent
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.FragmentWrapperActivity

class SmsExportActivity : FragmentWrapperActivity() {
  override fun getFragment(): Fragment {
    return NavHostFragment.create(R.navigation.sms_export)
  }

  companion object {
    fun createIntent(context: Context): Intent = Intent(context, SmsExportActivity::class.java)
  }
}
