package org.thoughtcrime.securesms.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.thoughtcrime.securesms.util.ServiceUtil
import org.thoughtcrime.securesms.util.TextSecurePreferences

/**
 * Respond to a PanicKit trigger Intent by locking the app.  PanicKit provides a
 * common framework for creating "panic button" apps that can trigger actions
 * in "panic responder" apps.  In this case, the response is to lock the app,
 * if it has been configured to do so via the Signal lock preference.
 */
class PanicResponderListener : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val passwordEnabled = !TextSecurePreferences.isPasswordDisabled(context)
    val keyguardSecure = ServiceUtil.getKeyguardManager(context).isKeyguardSecure
    val intentAction = intent.action
    if ((passwordEnabled || keyguardSecure) && "info.guardianproject.panic.action.TRIGGER" == intentAction) {
      val lockIntent = Intent(context, KeyCachingService::class.java)
      lockIntent.action = KeyCachingService.CLEAR_KEY_ACTION
      context.startService(lockIntent)
    }
  }
}
