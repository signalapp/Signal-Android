package org.thoughtcrime.securesms.attachments

import android.os.Parcelable
import com.fasterxml.jackson.annotation.JsonProperty
import kotlinx.parcelize.Parcelize

@Parcelize
data class AttachmentId(
  @JsonProperty("rowId")
  @JvmField
  val id: Long
) : Parcelable {

  val isValid: Boolean
    get() = id >= 0

  override fun toString(): String {
    return "AttachmentId::$id"
  }
}
