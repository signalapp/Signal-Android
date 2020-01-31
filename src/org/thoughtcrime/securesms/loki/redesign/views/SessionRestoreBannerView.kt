package org.thoughtcrime.securesms.loki.redesign.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import kotlinx.android.synthetic.main.session_restore_banner.view.*

import network.loki.messenger.R
import org.thoughtcrime.securesms.recipients.Recipient

class SessionRestoreBannerView : LinearLayout {
  lateinit var recipient: Recipient
  var onDismiss: (() -> Unit)? = null
  var onRestore: (() -> Unit)? = null

  constructor(context: Context) : super(context, null)
  constructor(context: Context, attrs: AttributeSet?) : super(context, attrs, 0)
  constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

  init {
    LayoutInflater.from(context).inflate(R.layout.session_restore_banner, this, true)
    restoreButton.setOnClickListener { onRestore?.invoke() }
    dismissButton.setOnClickListener { onDismiss?.invoke() }
  }

  fun update(recipient: Recipient) {
    this.recipient = recipient
    messageTextView.text = context.getString(R.string.session_restore_banner_message, recipient.toShortString())
  }

  fun show() {
    sessionRestoreBanner.visibility = View.VISIBLE
  }

  fun hide() {
    sessionRestoreBanner.visibility = View.GONE
  }
}
