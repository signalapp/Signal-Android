package org.thoughtcrime.securesms

import android.content.Context
import android.content.Intent
import android.provider.ContactsContract
import android.text.TextUtils
import androidx.annotation.WorkerThread
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.conversation.ConversationIntents
import org.thoughtcrime.securesms.conversation.NewConversationActivity
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.Rfc5724Uri
import java.net.URISyntaxException

class SystemContactsEntrypointViewModel : ViewModel() {

  companion object {
    private val TAG = Log.tag(SystemContactsEntrypointViewModel::class.java)
  }

  private val internalContactAction = MutableLiveData<ContactAction>()
  val contactAction: LiveData<ContactAction> = internalContactAction

  fun resolveNextStep(original: Intent) {
    viewModelScope.launch {
      val result = withContext(Dispatchers.IO) {
        getContactAction(AppDependencies.application, original)
      }

      internalContactAction.value = result
    }
  }

  @WorkerThread
  private fun getContactAction(context: Context, original: Intent): ContactAction {
    val destination = if (original.data != null && "content" == original.data?.scheme) {
      getDestinationForSyncAdapter(context, original)
    } else {
      getDestinationForView(original)
    }

    val destinationAddress = destination.destination
    if (TextUtils.isEmpty(destinationAddress)) {
      return ContactAction(NewConversationActivity.createIntent(context, destination.body), true)
    }

    val recipient = Recipient.external(destinationAddress!!)

    if (recipient != null) {
      val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(recipient)

      val nextIntent = ConversationIntents.createBuilderSync(context, recipient.id, threadId)
        .withDraftText(destination.body)
        .build()

      return ContactAction(nextIntent, false)
    }

    return ContactAction(NewConversationActivity.createIntent(context, destination.body), true)
  }

  private fun getDestinationForView(intent: Intent): DestinationAndBody {
    return try {
      val smsUri = Rfc5724Uri(intent.data.toString())
      DestinationAndBody(smsUri.path, smsUri.queryParams["body"])
    } catch (e: URISyntaxException) {
      Log.w(TAG, "unable to parse RFC5724 URI from intent", e)
      DestinationAndBody("", "")
    }
  }

  private fun getDestinationForSyncAdapter(context: Context, intent: Intent): DestinationAndBody {
    val uri = intent.data
    if (uri == null || uri.authority != ContactsContract.AUTHORITY) {
      Log.w(TAG, "Ignoring content URI with an unexpected authority.")
      return DestinationAndBody("", "")
    }

    context.contentResolver.query(uri, null, null, null, null).use { cursor ->
      if (cursor != null && cursor.moveToNext()) {
        return DestinationAndBody(cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.RawContacts.Data.DATA1)), "")
      }

      return DestinationAndBody("", "")
    }
  }

  data class ContactAction(
    val intent: Intent,
    val showSpecifyRecipientToast: Boolean
  )

  private data class DestinationAndBody(
    val destination: String?,
    val body: String?
  )
}
