package org.thoughtcrime.securesms.registration.fragments

import android.content.Context
import android.content.Intent
import android.text.SpannableStringBuilder
import android.view.View
import android.widget.Toast
import androidx.annotation.StringRes
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.logsubmit.SubmitDebugLogActivity
import org.thoughtcrime.securesms.phonenumbers.PhoneNumberFormatter
import org.thoughtcrime.securesms.util.SpanUtil

object RegistrationViewDelegate {

  @JvmStatic
  fun setDebugLogSubmitMultiTapView(view: View?) {
    view?.setOnClickListener(object : View.OnClickListener {
      private val DEBUG_TAP_TARGET = 8
      private val DEBUG_TAP_ANNOUNCE = 4
      private var debugTapCounter = 0

      override fun onClick(view: View) {
        debugTapCounter++

        if (debugTapCounter >= DEBUG_TAP_TARGET) {
          view.context.startActivity(Intent(view.context, SubmitDebugLogActivity::class.java))
        } else if (debugTapCounter >= DEBUG_TAP_ANNOUNCE) {
          val remaining = DEBUG_TAP_TARGET - debugTapCounter
          Toast.makeText(view.context, view.context.resources.getQuantityString(R.plurals.RegistrationActivity_debug_log_hint, remaining, remaining), Toast.LENGTH_SHORT).show()
        }
      }
    })
  }

  @JvmStatic
  fun showConfirmNumberDialogIfTranslated(
    context: Context,
    @StringRes title: Int?,
    @StringRes firstMessageLine: Int?,
    e164number: String,
    onConfirmed: Runnable,
    onEditNumber: Runnable
  ) {
    val message: CharSequence = SpannableStringBuilder().apply {
      append(SpanUtil.bold(PhoneNumberFormatter.prettyPrint(e164number)))
      if (firstMessageLine != null) {
        append("\n\n")
        append(context.getString(firstMessageLine))
      }
    }

    MaterialAlertDialogBuilder(context).apply {
      if (title != null) {
        setTitle(title)
      }
      setMessage(message)
      setPositiveButton(android.R.string.ok) { _, _ -> onConfirmed.run() }
      setNegativeButton(R.string.RegistrationActivity_edit_number) { _, _ -> onEditNumber.run() }
      setOnCancelListener { onEditNumber.run() }
    }.show()
  }
}
