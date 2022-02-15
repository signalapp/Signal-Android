package org.thoughtcrime.securesms.components.voice

import android.net.Uri
import org.thoughtcrime.securesms.database.DraftDatabase
import java.lang.IllegalArgumentException

private const val SIZE = "size"

class VoiceNoteDraft(
  val uri: Uri,
  val size: Long
) {
  companion object {
    @JvmStatic
    fun fromDraft(draft: DraftDatabase.Draft): VoiceNoteDraft {
      if (draft.type != DraftDatabase.Draft.VOICE_NOTE) {
        throw IllegalArgumentException()
      }

      val draftUri = Uri.parse(draft.value)

      val uri: Uri = draftUri.buildUpon().clearQuery().build()
      val size: Long = draftUri.getQueryParameter("size")!!.toLong()

      return VoiceNoteDraft(uri, size)
    }
  }

  fun asDraft(): DraftDatabase.Draft {
    val draftUri = uri.buildUpon().appendQueryParameter(SIZE, size.toString())

    return DraftDatabase.Draft(DraftDatabase.Draft.VOICE_NOTE, draftUri.build().toString())
  }
}
