/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription.receipts

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.core.app.ShareCompat
import androidx.core.view.drawToBitmap
import kotlinx.coroutines.withContext
import org.signal.core.util.concurrent.SignalDispatchers
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.database.model.InAppPaymentReceiptRecord
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.payments.FiatMoneyUtil
import org.thoughtcrime.securesms.util.DateUtils
import java.io.ByteArrayOutputStream
import java.util.Locale

/**
 * Generates a receipt PNG for an in-app payment.
 */
object ReceiptImageRenderer {

  private const val DONATION_RECEIPT_WIDTH = 1916

  suspend fun renderPng(context: Context, record: InAppPaymentReceiptRecord): Uri {
    val today: String = DateUtils.formatDateWithDayOfWeek(Locale.getDefault(), System.currentTimeMillis())
    val amount: String = FiatMoneyUtil.format(context.resources, record.amount)
    val type: String = context.getString(record.type.labelRes)
    val datePaid: String = DateUtils.formatDate(Locale.getDefault(), record.timestamp)

    return withContext(SignalDispatchers.Default) {
      val outputStream = ByteArrayOutputStream()
      val view = LayoutInflater
        .from(context)
        .inflate(R.layout.donation_receipt_png, null)

      view.layoutDirection = context.resources.configuration.layoutDirection

      view.findViewById<TextView>(R.id.date).text = today
      view.findViewById<TextView>(R.id.amount).text = amount
      view.findViewById<TextView>(R.id.donation_type).text = type
      view.findViewById<TextView>(R.id.date_paid).text = datePaid

      view.measure(View.MeasureSpec.makeMeasureSpec(DONATION_RECEIPT_WIDTH, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
      view.layout(0, 0, view.measuredWidth, view.measuredHeight)

      val bitmap = view.drawToBitmap()
      bitmap.compress(Bitmap.CompressFormat.PNG, 0, outputStream)

      AppDependencies.blobs
        .forData(outputStream.toByteArray())
        .withMimeType("image/png")
        .withFileName("Signal-Donation-Receipt.png")
        .createForSingleSessionInMemory()
    }
  }

  fun createShareIntent(context: Context, uri: Uri): Intent {
    return ShareCompat.IntentBuilder(context)
      .setStream(uri)
      .setType(Intent.normalizeMimeType("image/png"))
      .createChooserIntent()
      .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
  }
}
