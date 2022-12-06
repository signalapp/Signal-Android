package org.thoughtcrime.securesms.conversation.drafts

import android.content.Context
import android.net.Uri
import android.text.Spannable
import android.text.SpannableString
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.concurrent.SignalExecutors
import org.thoughtcrime.securesms.components.mention.MentionAnnotation
import org.thoughtcrime.securesms.database.DraftTable
import org.thoughtcrime.securesms.database.DraftTable.Drafts
import org.thoughtcrime.securesms.database.MentionUtil
import org.thoughtcrime.securesms.database.MmsSmsColumns
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.ThreadTable
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.providers.BlobProvider
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.Base64
import org.thoughtcrime.securesms.util.concurrent.SerialMonoLifoExecutor
import java.util.concurrent.Executor

class DraftRepository(
  private val context: Context = ApplicationDependencies.getApplication(),
  private val threadTable: ThreadTable = SignalDatabase.threads,
  private val draftTable: DraftTable = SignalDatabase.drafts,
  private val saveDraftsExecutor: Executor = SerialMonoLifoExecutor(SignalExecutors.BOUNDED)
) {

  fun deleteVoiceNoteDraftData(draft: DraftTable.Draft?) {
    if (draft != null) {
      SignalExecutors.BOUNDED.execute {
        BlobProvider.getInstance().delete(context, Uri.parse(draft.value).buildUpon().clearQuery().build())
      }
    }
  }

  fun saveDrafts(recipient: Recipient, threadId: Long, distributionType: Int, drafts: Drafts) {
    saveDraftsExecutor.execute {
      if (drafts.isNotEmpty()) {
        val actualThreadId = if (threadId == -1L) {
          threadTable.getOrCreateThreadIdFor(recipient, distributionType)
        } else {
          threadId
        }

        draftTable.replaceDrafts(actualThreadId, drafts)
        threadTable.updateSnippet(actualThreadId, drafts.getSnippet(context), drafts.getUriSnippet(), System.currentTimeMillis(), MmsSmsColumns.Types.BASE_DRAFT_TYPE, true)
      } else if (threadId > 0) {
        draftTable.clearDrafts(threadId)
        threadTable.update(threadId, unarchive = false, allowDeletion = false)
      }
    }
  }

  fun loadDrafts(threadId: Long): Single<DatabaseDraft> {
    return Single.fromCallable {
      val drafts: Drafts = draftTable.getDrafts(threadId)
      val mentionsDraft = drafts.getDraftOfType(DraftTable.Draft.MENTION)
      var updatedText: Spannable? = null

      if (mentionsDraft != null) {
        val text = drafts.getDraftOfType(DraftTable.Draft.TEXT)!!.value
        val mentions = MentionUtil.bodyRangeListToMentions(context, Base64.decodeOrThrow(mentionsDraft.value))
        val updated = MentionUtil.updateBodyAndMentionsWithDisplayNames(context, text, mentions)
        updatedText = SpannableString(updated.body)
        MentionAnnotation.setMentionAnnotations(updatedText, updated.mentions)
      }

      DatabaseDraft(drafts, updatedText)
    }.subscribeOn(Schedulers.io())
  }

  data class DatabaseDraft(val drafts: Drafts, val updatedText: CharSequence?)
}
