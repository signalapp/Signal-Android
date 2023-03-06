package org.thoughtcrime.securesms.util.dualsim

import android.content.Context
import android.content.pm.PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

/**
 * The mobile country code consists of three decimal digits and the mobile network code consists of two or three decimal digits.
 */
class MccMncProducer(context: Context) {
  var mcc: String? = null
    private set
  var mnc: String? = null
    private set

  init {
    if (context.packageManager.hasSystemFeature(FEATURE_TELEPHONY_RADIO_ACCESS)) {
      val tel = ContextCompat.getSystemService(context, TelephonyManager::class.java)
      val networkOperator = tel?.networkOperator

      if (networkOperator?.isNotBlank() == true && networkOperator.length >= 5) {
        mcc = networkOperator.substring(0, 3)
        mnc = networkOperator.substring(3)
      }
    }
  }
}
