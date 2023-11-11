@file:JvmName("DatabaseProtosUtil")

package org.thoughtcrime.securesms.database.model

import org.thoughtcrime.securesms.database.model.databaseprotos.BodyRangeList
import org.thoughtcrime.securesms.database.model.databaseprotos.PendingOneTimeDonation
import org.whispersystems.signalservice.internal.push.BodyRange
import kotlin.time.Duration.Companion.days

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

fun PendingOneTimeDonation?.isPending(): Boolean {
  return this != null && this.error == null && !this.isExpired
}

fun PendingOneTimeDonation?.isLongRunning(): Boolean {
  return isPending() && this!!.paymentMethodType == PendingOneTimeDonation.PaymentMethodType.SEPA_DEBIT
}

val PendingOneTimeDonation.isExpired: Boolean
  get() {
    val pendingOneTimeBankTransferTimeout = 14.days
    val pendingOneTimeNormalTimeout = 1.days

    val timeout = if (paymentMethodType == PendingOneTimeDonation.PaymentMethodType.SEPA_DEBIT) {
      pendingOneTimeBankTransferTimeout
    } else {
      pendingOneTimeNormalTimeout
    }

    return (timestamp + timeout.inWholeMilliseconds) < System.currentTimeMillis()
  }
