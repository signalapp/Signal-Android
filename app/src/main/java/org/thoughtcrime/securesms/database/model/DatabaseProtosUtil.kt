@file:JvmName("DatabaseProtosUtil")

package org.thoughtcrime.securesms.database.model

import com.google.protobuf.ByteString
import org.thoughtcrime.securesms.database.model.databaseprotos.BodyRangeList
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.BodyRange

/**
 * Collection of extensions to make working with database protos cleaner.
 */

fun ByteArray.toProtoByteString(): ByteString {
  return ByteString.copyFrom(this)
}

fun BodyRangeList.Builder.addStyle(style: BodyRangeList.BodyRange.Style, start: Int, length: Int): BodyRangeList.Builder {
  addRanges(
    BodyRangeList.BodyRange.newBuilder()
      .setStyle(style)
      .setStart(start)
      .setLength(length)
  )

  return this
}

fun BodyRangeList.Builder.addLink(link: String, start: Int, length: Int): BodyRangeList.Builder {
  addRanges(
    BodyRangeList.BodyRange.newBuilder()
      .setLink(link)
      .setStart(start)
      .setLength(length)
  )

  return this
}

fun BodyRangeList.Builder.addButton(label: String, action: String, start: Int, length: Int): BodyRangeList.Builder {
  addRanges(
    BodyRangeList.BodyRange.newBuilder()
      .setButton(BodyRangeList.BodyRange.Button.newBuilder().setLabel(label).setAction(action))
      .setStart(start)
      .setLength(length)
  )

  return this
}

fun List<BodyRange>?.toBodyRangeList(): BodyRangeList? {
  if (this == null) {
    return null
  }

  val builder = BodyRangeList.newBuilder()

  for (bodyRange in this) {
    var style: BodyRangeList.BodyRange.Style? = null
    when (bodyRange.style) {
      BodyRange.Style.BOLD -> style = BodyRangeList.BodyRange.Style.BOLD
      BodyRange.Style.ITALIC -> style = BodyRangeList.BodyRange.Style.ITALIC
      BodyRange.Style.SPOILER -> style = BodyRangeList.BodyRange.Style.SPOILER
      BodyRange.Style.STRIKETHROUGH -> style = BodyRangeList.BodyRange.Style.STRIKETHROUGH
      BodyRange.Style.MONOSPACE -> style = BodyRangeList.BodyRange.Style.MONOSPACE
      else -> Unit
    }
    if (style != null) {
      builder.addStyle(style, bodyRange.start, bodyRange.length)
    }
  }

  return builder.build()
}
