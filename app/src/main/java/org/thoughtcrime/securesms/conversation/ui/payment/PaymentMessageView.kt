package org.thoughtcrime.securesms.conversation.ui.payment

import android.content.Context
import android.content.res.ColorStateList
import android.text.style.TypefaceSpan
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.annotation.ColorInt
import androidx.core.view.ViewCompat
import com.google.android.material.progressindicator.CircularProgressIndicatorSpec
import com.google.android.material.progressindicator.IndeterminateDrawable
import org.signal.core.util.dp
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.quotes.QuoteViewColorTheme
import org.thoughtcrime.securesms.conversation.colors.Colorizer
import org.thoughtcrime.securesms.database.model.databaseprotos.PaymentTombstone
import org.thoughtcrime.securesms.databinding.PaymentMessageViewBinding
import org.thoughtcrime.securesms.payments.CryptoValueUtil
import org.thoughtcrime.securesms.payments.Direction
import org.thoughtcrime.securesms.payments.Payment
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.visible
import org.whispersystems.signalservice.api.payments.Money

/**
 * Showing payment information in conversation.
 */
class PaymentMessageView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

  private val binding: PaymentMessageViewBinding

  private var onTombstoneClickListener: OnClickListener? = null

  init {
    binding = PaymentMessageViewBinding.inflate(LayoutInflater.from(context), this, true)
  }

  fun bindPayment(recipient: Recipient, payment: Payment?, colorizer: Colorizer) {
    if (payment == null) {
      binding.paymentTombstone.visible = true
      binding.paymentAmount.visible = false
      binding.paymentInprogress.visible = false
      binding.paymentAmountLayout.setOnClickListener {
        onTombstoneClickListener?.onClick(it)
      }
      return
    }

    binding.paymentAmountLayout.setOnClickListener(null)
    binding.paymentTombstone.visible = false
    val outgoing = payment.direction == Direction.SENT

    binding.paymentDirection.apply {
      if (outgoing) {
        text = context.getString(R.string.PaymentMessageView_you_sent_s, recipient.getShortDisplayName(context))
        setTextColor(colorizer.getOutgoingFooterTextColor(context))
      } else {
        text = context.getString(R.string.PaymentMessageView_s_sent_you, recipient.getShortDisplayName(context))
        setTextColor(colorizer.getIncomingFooterTextColor(context, recipient.hasWallpaper))
      }
    }

    binding.paymentNote.apply {
      text = payment.note
      visible = payment.note.isNotEmpty()
      setTextColor(if (outgoing) colorizer.getOutgoingBodyTextColor(context) else colorizer.getIncomingBodyTextColor(context, recipient.hasWallpaper))
    }

    val quoteViewColorTheme = QuoteViewColorTheme.resolveTheme(outgoing, false, recipient.hasWallpaper)

    if (payment.state.isInProgress) {
      binding.paymentAmount.visible = false
      binding.paymentInprogress.visible = true
      binding.paymentInprogress.setImageDrawable(getInProgressDrawable(quoteViewColorTheme.getForegroundColor(context)))
    } else {
      binding.paymentAmount.visible = true
      binding.paymentInprogress.visible = false
      binding.paymentAmount.setTextColor(quoteViewColorTheme.getForegroundColor(context))
      binding.paymentAmount.setMoney(payment.amount, 0L, currencyTypefaceSpan)
    }

    ViewCompat.setBackgroundTintList(binding.paymentAmountLayout, ColorStateList.valueOf(quoteViewColorTheme.getBackgroundColor(context)))
  }

  fun bindPaymentTombstone(outgoing: Boolean, recipient: Recipient, paymentTombstone: PaymentTombstone?, colorizer: Colorizer) {
    val amount: Money? = if (paymentTombstone?.amount != null) {
      CryptoValueUtil.cryptoValueToMoney(paymentTombstone.amount)
    } else {
      null
    }

    binding.paymentDirection.apply {
      if (outgoing) {
        text = context.getString(R.string.PaymentMessageView_you_sent_s, recipient.getShortDisplayName(context))
        setTextColor(colorizer.getOutgoingFooterTextColor(context))
      } else {
        text = context.getString(R.string.PaymentMessageView_s_sent_you, recipient.getShortDisplayName(context))
        setTextColor(colorizer.getIncomingFooterTextColor(context, recipient.hasWallpaper))
      }
    }
    if (amount == null) {
      binding.paymentTombstone.visible = true
      binding.paymentAmount.visible = false
      binding.paymentInprogress.visible = false
      binding.paymentAmountLayout.setOnClickListener {
        onTombstoneClickListener?.onClick(it)
      }
      return
    }
    val note = paymentTombstone?.note ?: ""
    binding.paymentAmountLayout.setOnClickListener(null)
    binding.paymentTombstone.visible = false

    binding.paymentNote.apply {
      text = note
      visible = note.isNotEmpty()
      setTextColor(if (outgoing) colorizer.getOutgoingBodyTextColor(context) else colorizer.getIncomingBodyTextColor(context, recipient.hasWallpaper))
    }

    val quoteViewColorTheme = QuoteViewColorTheme.resolveTheme(outgoing, false, recipient.hasWallpaper)

    binding.paymentAmount.visible = true
    binding.paymentInprogress.visible = false
    binding.paymentAmount.setTextColor(quoteViewColorTheme.getForegroundColor(context))
    binding.paymentAmount.setMoney(amount, 0L, currencyTypefaceSpan)

    ViewCompat.setBackgroundTintList(binding.paymentAmountLayout, ColorStateList.valueOf(quoteViewColorTheme.getBackgroundColor(context)))
  }

  private fun getInProgressDrawable(@ColorInt color: Int): IndeterminateDrawable<CircularProgressIndicatorSpec> {
    val spec = CircularProgressIndicatorSpec(context, null).apply {
      indicatorInset = 0
      indicatorColors = intArrayOf(color)
      indicatorSize = 20.dp
      trackThickness = 2.dp
    }

    val drawable = IndeterminateDrawable.createCircularDrawable(context, spec)
    drawable.setBounds(0, 0, spec.indicatorSize, spec.indicatorSize)
    return drawable
  }

  fun setOnTombstoneClickListener(listener: OnClickListener?) {
    this.onTombstoneClickListener = listener
  }

  companion object {
    private val currencyTypefaceSpan = TypefaceSpan("sans-serif-light")
  }
}
