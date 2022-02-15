package org.thoughtcrime.securesms.database.model

import org.thoughtcrime.securesms.database.model.databaseprotos.BodyRangeList

/**
 * Collection of extensions to make working with database protos cleaner.
 */

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
