package org.thoughtcrime.securesms.loki

import org.thoughtcrime.securesms.components.reminder.Reminder

import android.annotation.TargetApi
import android.content.Context
import android.os.Build.VERSION_CODES
import android.text.TextUtils
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.android.synthetic.main.session_restore_banner.view.*

import network.loki.messenger.R
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.ViewUtil

/**
 * View to display actionable reminders to the user
 */
class SessionRestoreBannerView private constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : LinearLayout(context, attrs, defStyleAttr) {
  private var container: ViewGroup? = null
  private var closeButton: ImageButton? = null
  private var title: TextView? = null
  private var text: TextView? = null
  lateinit var recipient: Recipient
  var onDismiss: (() -> Unit)? = null
  var onRestore: (() -> Unit)? = null

  constructor(context: Context) : this(context, null)
  private constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

  init {
    LayoutInflater.from(context).inflate(R.layout.session_restore_banner, this, true)
    restoreButton.setOnClickListener { onRestore?.invoke() }
    dismissButton.setOnClickListener { onDismiss?.invoke() }
  }

  fun update(recipient: Recipient) {
    this.recipient = recipient
    restoreText.text = context.getString(R.string.session_restore_banner_message, recipient.toShortString())
  }

  fun show() {
    container!!.visibility = View.VISIBLE;
  }

  fun hide() {
    container!!.visibility = View.GONE
  }
}
