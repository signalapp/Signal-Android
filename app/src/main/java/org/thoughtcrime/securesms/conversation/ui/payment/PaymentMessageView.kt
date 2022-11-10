package org.thoughtcrime.securesms.conversation.ui.payment

import android.content.Context
import android.content.res.ColorStateList
import android.text.style.TypefaceSpan
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.quotes.QuoteViewColorTheme
import org.thoughtcrime.securesms.conversation.colors.Colorizer
import org.thoughtcrime.securesms.databinding.PaymentMessageViewBinding
import org.thoughtcrime.securesms.payments.Direction
import org.thoughtcrime.securesms.payments.Payment
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.visible

/**
 * Showing payment information in conversation.
 */
class PaymentMessageView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

  private val binding: PaymentMessageViewBinding

  init {
    binding = PaymentMessageViewBinding.inflate(LayoutInflater.from(context), this, true)
  }

  fun bindPayment(recipient: Recipient, payment: Payment, colorizer: Colorizer) {
    val outgoing = payment.direction == Direction.SENT

    binding.paymentDirection.apply {
      if (outgoing) {
        text = context.getString(R.string.PaymentMessageView_you_sent_s, recipient.getShortDisplayName(context))
        setTextColor(colorizer.getOutgoingFooterTextColor(context))
      } else {
        text = context.getString(R.string.PaymentMessageView_s_sent_you, recipient.getShortDisplayName(context))
        setTextColor(colorizer.getIncomingFooterTextColor(context, recipient.hasWallpaper()))
      }
    }

    binding.paymentNote.apply {
      text = payment.note
      visible = payment.note.isNotEmpty()
      setTextColor(if (outgoing) colorizer.getOutgoingBodyTextColor(context) else colorizer.getIncomingBodyTextColor(context, recipient.hasWallpaper()))
    }

    val quoteViewColorTheme = QuoteViewColorTheme.resolveTheme(outgoing, false, recipient.hasWallpaper())

    binding.paymentAmount.setTextColor(quoteViewColorTheme.getForegroundColor(context))
    binding.paymentAmount.setMoney(payment.amount, 0L, currencyTypefaceSpan)

    ViewCompat.setBackgroundTintList(binding.paymentAmountLayout, ColorStateList.valueOf(quoteViewColorTheme.getBackgroundColor(context)))
  }

  companion object {
    private val currencyTypefaceSpan = TypefaceSpan("sans-serif-light")
  }
}
