@file:JvmName("BodyRangeUtil")

package org.thoughtcrime.securesms.database

import org.thoughtcrime.securesms.database.model.databaseprotos.BodyRangeList

/**
 * Given a list of body adjustment from removing mention names from a message and replacing
 * them with a placeholder, we need to adjust the ranges within [BodyRangeList] to account
 * for the now shorter text.
 */
fun BodyRangeList?.adjustBodyRanges(bodyAdjustments: List<BodyAdjustment>): BodyRangeList? {
  if (this == null || bodyAdjustments.isEmpty()) {
    return this
  }

  val newBodyRanges = rangesList.toMutableList()

  for (adjustment in bodyAdjustments) {
    val adjustmentLength = adjustment.oldLength - adjustment.newLength

    rangesList.forEachIndexed { listIndex, range ->
      val needsRangeStartsAfterAdjustment = range.start > adjustment.startIndex
      val needsRangeCoversAdjustment = range.start <= adjustment.startIndex && range.start + range.length >= adjustment.startIndex + adjustment.oldLength

      val newRange = newBodyRanges[listIndex]
      val newStart: Int? = if (needsRangeStartsAfterAdjustment) newRange.start - adjustmentLength else null
      val newLength: Int? = if (needsRangeCoversAdjustment) newRange.length - adjustmentLength else null

      if (newStart != null || newLength != null) {
        newBodyRanges[listIndex] = newRange.toBuilder().setStart(newStart ?: newRange.start).setLength(newLength ?: newRange.length).build()
      }
    }
  }

  return BodyRangeList.newBuilder().addAllRanges(newBodyRanges).build()
}
