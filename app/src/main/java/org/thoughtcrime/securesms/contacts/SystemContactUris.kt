package org.thoughtcrime.securesms.contacts

import android.content.Context
import android.net.Uri
import androidx.annotation.WorkerThread
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.signal.contacts.SystemContactsRepository
import org.signal.core.util.concurrent.SignalDispatchers
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.recipients.Recipient

object SystemContactUris {

  private val TAG = Log.tag(SystemContactUris::class.java)

  suspend fun resolve(context: Context, recipient: Recipient): Uri? = withContext(SignalDispatchers.IO) {
    val storedUri = recipient.contactUri ?: return@withContext null

    val currentUri = try {
      SystemContactsRepository.currentContactUri(context, storedUri)
    } catch (e: Exception) {
      Log.w(TAG, "Unable to resolve the system contact, using the stored URI.", e)
      null
    }

    currentUri ?: storedUri
  }

  @JvmStatic
  @WorkerThread
  fun resolveSync(context: Context, recipient: Recipient): Uri? = runBlocking { resolve(context, recipient) }
}
