/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription.receipts

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ShareCompat
import androidx.core.view.drawToBitmap
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.database.model.InAppPaymentReceiptRecord
import org.thoughtcrime.securesms.payments.FiatMoneyUtil
import org.thoughtcrime.securesms.providers.BlobProvider
import org.thoughtcrime.securesms.util.DateUtils
import java.io.ByteArrayOutputStream
import java.util.Locale

/**
 * Generates a receipt PNG for an in-app payment.
 */
object ReceiptImageRenderer {

  private const val DONATION_RECEIPT_WIDTH = 1916
  private val TAG = Log.tag(ReceiptImageRenderer::class.java)

  fun renderPng(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    record: InAppPaymentReceiptRecord,
    subscriptionName: String,
    callback: Callback
  ) {
    val today: String = DateUtils.formatDateWithDayOfWeek(Locale.getDefault(), System.currentTimeMillis())
    val amount: String = FiatMoneyUtil.format(context.resources, record.amount)
    val type: String = when (record.type) {
      InAppPaymentReceiptRecord.Type.RECURRING_DONATION, InAppPaymentReceiptRecord.Type.RECURRING_BACKUP -> context.getString(R.string.DonationReceiptDetailsFragment__s_dash_s, subscriptionName, context.getString(R.string.DonationReceiptListFragment__recurring))
      InAppPaymentReceiptRecord.Type.ONE_TIME_DONATION -> context.getString(R.string.DonationReceiptListFragment__one_time)
      InAppPaymentReceiptRecord.Type.ONE_TIME_GIFT -> context.getString(R.string.DonationReceiptListFragment__donation_for_a_friend)
    }
    val datePaid: String = DateUtils.formatDate(Locale.getDefault(), record.timestamp)

    lifecycleOwner.lifecycleScope.launch {
      val bitmapUri: Uri = withContext(Dispatchers.Default) {
        val outputStream = ByteArrayOutputStream()
        val view = LayoutInflater
          .from(context)
          .inflate(R.layout.donation_receipt_png, null)

        view.findViewById<TextView>(R.id.date).text = today
        view.findViewById<TextView>(R.id.amount).text = amount
        view.findViewById<TextView>(R.id.donation_type).text = type
        view.findViewById<TextView>(R.id.date_paid).text = datePaid

        view.measure(View.MeasureSpec.makeMeasureSpec(DONATION_RECEIPT_WIDTH, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)

        val bitmap = view.drawToBitmap()
        bitmap.compress(Bitmap.CompressFormat.PNG, 0, outputStream)

        BlobProvider.getInstance()
          .forData(outputStream.toByteArray())
          .withMimeType("image/png")
          .withFileName("Signal-Donation-Receipt.png")
          .createForSingleSessionInMemory()
      }

      withContext(Dispatchers.Main) {
        callback.onBitmapRendered()
        openShareSheet(context, bitmapUri, callback)
      }
    }
  }

  private fun openShareSheet(context: Context, uri: Uri, callback: Callback) {
    val mimeType = Intent.normalizeMimeType("image/png")
    val shareIntent = ShareCompat.IntentBuilder(context)
      .setStream(uri)
      .setType(mimeType)
      .createChooserIntent()
      .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    try {
      callback.onStartActivity(shareIntent)
    } catch (e: ActivityNotFoundException) {
      Log.w(TAG, "No activity existed to share the media.", e)
      Toast.makeText(context, R.string.MediaPreviewActivity_cant_find_an_app_able_to_share_this_media, Toast.LENGTH_LONG).show()
    }
  }

  interface Callback {
    fun onBitmapRendered()
    fun onStartActivity(intent: Intent)
  }
}
