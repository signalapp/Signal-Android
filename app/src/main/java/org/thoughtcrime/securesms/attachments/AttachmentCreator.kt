/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.attachments

import android.os.Parcel
import android.os.Parcelable

/**
 * Parcelable Creator for Attachments. Encapsulates the logic around dealing with
 * subclasses, since Attachment is abstract and has several children.
 */
object AttachmentCreator : Parcelable.Creator<Attachment> {
  enum class Subclass(val clazz: Class<out Attachment>, val code: String) {
    DATABASE(DatabaseAttachment::class.java, "database"),
    POINTER(PointerAttachment::class.java, "pointer"),
    TOMBSTONE(TombstoneAttachment::class.java, "tombstone"),
    URI(UriAttachment::class.java, "uri")
  }

  @JvmStatic
  fun writeSubclass(dest: Parcel, instance: Attachment) {
    val subclass = Subclass.values().firstOrNull { it.clazz == instance::class.java } ?: error("Unexpected subtype ${instance::class.java.simpleName}")
    dest.writeString(subclass.code)
  }

  override fun createFromParcel(source: Parcel): Attachment {
    val rawCode = source.readString()!!

    return when (Subclass.values().first { rawCode == it.code }) {
      Subclass.DATABASE -> DatabaseAttachment(source)
      Subclass.POINTER -> PointerAttachment(source)
      Subclass.TOMBSTONE -> TombstoneAttachment(source)
      Subclass.URI -> UriAttachment(source)
    }
  }

  override fun newArray(size: Int): Array<Attachment?> = arrayOfNulls(size)
}
