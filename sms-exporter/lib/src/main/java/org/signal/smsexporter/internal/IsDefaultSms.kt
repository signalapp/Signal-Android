package org.signal.smsexporter.internal

import android.app.role.RoleManager
import android.content.Context
import android.os.Build
import android.provider.Telephony
import androidx.core.role.RoleManagerCompat

/**
 * Uses the appropriate service to check if we are the default sms
 */
internal object IsDefaultSms {
  fun checkIsDefaultSms(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= 29) {
      context.getSystemService(RoleManager::class.java).isRoleHeld(RoleManagerCompat.ROLE_SMS)
    } else {
      context.packageName == Telephony.Sms.getDefaultSmsPackage(context)
    }
  }
}
