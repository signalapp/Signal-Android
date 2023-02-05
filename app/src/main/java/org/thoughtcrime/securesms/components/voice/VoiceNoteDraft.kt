package org.thoughtcrime.securesms.components.voice

import android.net.Uri
import org.thoughtcrime.securesms.database.DraftTable

private const val SIZE = "size"

class VoiceNoteDraft(
  val uri: Uri,
  val size: Long
) {
  companion object {
    @JvmStatic
    fun fromDraft(draft: DraftTable.Draft): VoiceNoteDraft {
      if (draft.type != DraftTable.Draft.VOICE_NOTE) {
        throw IllegalArgumentException()
      }

      val draftUri = Uri.parse(draft.value)

      val uri: Uri = draftUri.buildUpon().clearQuery().build()
      val size: Long = draftUri.getQueryParameter("size")!!.toLong()

      return VoiceNoteDraft(uri, size)
    }
  }

  fun asDraft(): DraftTable.Draft {
    val draftUri = uri.buildUpon().appendQueryParameter(SIZE, size.toString())

    return DraftTable.Draft(DraftTable.Draft.VOICE_NOTE, draftUri.build().toString())
  }
}
