package org.signal.contactstest

import android.app.Service
import android.content.Intent
import android.os.IBinder

class ContactsSyncAdapterService : Service() {
  @Synchronized
  override fun onCreate() {
    if (syncAdapter == null) {
      syncAdapter = ContactsSyncAdapter(this, true)
    }
  }

  override fun onBind(intent: Intent): IBinder? {
    return syncAdapter!!.syncAdapterBinder
  }

  companion object {
    private var syncAdapter: ContactsSyncAdapter? = null
  }
}
