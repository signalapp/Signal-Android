package org.signal.contactstest

import android.accounts.Account
import android.content.AbstractThreadedSyncAdapter
import android.content.ContentProviderClient
import android.content.Context
import android.content.SyncResult
import android.os.Bundle
import org.signal.core.util.logging.Log

class ContactsSyncAdapter(context: Context?, autoInitialize: Boolean) : AbstractThreadedSyncAdapter(context, autoInitialize) {
  override fun onPerformSync(
    account: Account,
    extras: Bundle,
    authority: String,
    provider: ContentProviderClient,
    syncResult: SyncResult
  ) {
    Log.i(TAG, "onPerformSync()")
  }

  override fun onSyncCanceled() {
    Log.w(TAG, "onSyncCanceled()")
  }

  override fun onSyncCanceled(thread: Thread) {
    Log.w(TAG, "onSyncCanceled($thread)")
  }

  companion object {
    private val TAG = Log.tag(ContactsSyncAdapter::class.java)
  }
}
