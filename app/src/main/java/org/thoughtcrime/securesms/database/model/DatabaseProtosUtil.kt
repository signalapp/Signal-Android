@file:JvmName("DatabaseProtosUtil")

package org.thoughtcrime.securesms.database.model

import org.thoughtcrime.securesms.database.model.databaseprotos.BodyRangeList
import org.whispersystems.signalservice.internal.push.BodyRange

/**
 * Collection of extensions to make working with database protos cleaner.
 */
fun BodyRangeList.Builder.addStyle(style: BodyRangeList.BodyRange.Style, start: Int, length: Int): BodyRangeList.Builder {
  ranges += BodyRangeList.BodyRange(style = style, start = start, length = length)
  return this
}

fun BodyRangeList.Builder.addLink(link: String, start: Int, length: Int): BodyRangeList.Builder {
  ranges += BodyRangeList.BodyRange(link = link, start = start, length = length)
  return this
}

fun BodyRangeList.Builder.addButton(label: String, action: String, start: Int, length: Int): BodyRangeList.Builder {
  ranges += BodyRangeList.BodyRange(
    button = BodyRangeList.BodyRange.Button(label = label, action = action),
    start = start,
    length = length
  )

  return this
}

fun List<BodyRange>?.toBodyRangeList(): BodyRangeList? {
  if (this == null) {
    return null
  }

  val builder = BodyRangeList.Builder()

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
      builder.addStyle(style, bodyRange.start!!, bodyRange.length!!)
    }
  }

  return builder.build()
}
