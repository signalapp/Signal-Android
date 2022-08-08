package org.signal.smsexporter.app

import android.app.Service
import android.content.Intent
import android.os.IBinder

class SendResponseViaMessageService : Service() {
  override fun onBind(intent: Intent?): IBinder? {
    return null
  }
}
