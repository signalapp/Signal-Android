package org.thoughtcrime.securesms.polls

import android.os.Bundle
import android.os.Parcelable
import androidx.core.os.bundleOf
import kotlinx.parcelize.Parcelize

/**
 * Class to represent a poll when it's being created but not yet saved to the database
 */
@Parcelize
data class Poll(
  val question: String,
  val allowMultipleVotes: Boolean,
  val pollOptions: List<String>,
  val authorId: Long = -1
) : Parcelable {

  companion object {
    const val KEY_QUESTION = "question"
    const val KEY_ALLOW_MULTIPLE = "allow_multiple"
    const val KEY_OPTIONS = "options"

    @JvmStatic
    fun fromBundle(bundle: Bundle): Poll {
      return Poll(
        bundle.getString(KEY_QUESTION)!!,
        bundle.getBoolean(KEY_ALLOW_MULTIPLE),
        bundle.getStringArrayList(KEY_OPTIONS)!!
      )
    }
  }

  fun toBundle(): Bundle {
    return bundleOf(
      KEY_QUESTION to question,
      KEY_ALLOW_MULTIPLE to allowMultipleVotes,
      KEY_OPTIONS to ArrayList(pollOptions.toList())
    )
  }
}
