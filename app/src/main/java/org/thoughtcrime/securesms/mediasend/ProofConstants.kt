package org.thoughtcrime.securesms.mediasend

import android.os.SystemClock
import android.view.View

object ProofConstants {
  const val IS_PROOF_ENABLED = "IS_PROOF_ENABLED"
  const val IS_PROOF_NOTARY_ENABLED_GLOBAL = "IS_PROOF_NOTARY_ENABLED_GLOBAL"
  const val IS_PROOF_LOCATION_ENABLED_GLOBAL = "IS_PROOF_LOCATION_ENABLED_GLOBAL"
  const val IS_PROOF_PHONE_ENABLED_GLOBAL = "IS_PROOF_PHONE_ENABLED_GLOBAL"
  const val IS_PROOF_NETWORK_ENABLED_GLOBAL = "IS_PROOF_NETWORK_ENABLED_GLOBAL"
  const val IS_PROOF_NOTARY_ENABLED_LOCAL = "IS_PROOF_NOTARY_ENABLED_LOCAL"
  const val IS_PROOF_LOCATION_ENABLED_LOCAL = "IS_PROOF_LOCATION_ENABLED_LOCAL"
  const val IS_PROOF_PHONE_ENABLED_LOCAL = "IS_PROOF_PHONE_ENABLED_LOCAL"
  const val IS_PROOF_NETWORK_ENABLED_LOCAL = "IS_PROOF_NETWORK_ENABLED_LOCAL"
}

fun View.setOnClickListenerWithThrottle(throttleTime: Long = 600L, action: () -> Unit) {
  this.setOnClickListener(object : View.OnClickListener {
    private var lastClickTime: Long = 0

    override fun onClick(v: View) {
      if (SystemClock.elapsedRealtime() - lastClickTime < throttleTime) return
      else action()

      lastClickTime = SystemClock.elapsedRealtime()
    }
  })
}