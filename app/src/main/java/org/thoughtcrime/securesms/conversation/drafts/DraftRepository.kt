package org.thoughtcrime.securesms.conversation.drafts

import android.content.Context
import android.net.Uri
import org.signal.core.util.concurrent.SignalExecutors
import org.thoughtcrime.securesms.database.DraftDatabase
import org.thoughtcrime.securesms.providers.BlobProvider

class DraftRepository(private val context: Context) {
  fun deleteVoiceNoteDraft(draft: DraftDatabase.Draft) {
    deleteBlob(Uri.parse(draft.value).buildUpon().clearQuery().build())
  }

  fun deleteBlob(uri: Uri) {
    SignalExecutors.BOUNDED.execute {
      BlobProvider.getInstance().delete(context, uri)
    }
  }
}
