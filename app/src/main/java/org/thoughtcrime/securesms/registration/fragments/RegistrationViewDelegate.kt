package org.thoughtcrime.securesms.registration.fragments

import android.content.Context
import android.content.Intent
import android.text.SpannableStringBuilder
import android.view.View
import android.widget.Toast
import androidx.annotation.StringRes
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.signal.core.util.TranslationDetection
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
    @StringRes firstMessageLine: Int,
    e164number: String,
    onConfirmed: Runnable,
    onEditNumber: Runnable
  ) {
    val translationDetection = TranslationDetection(context)

    if (translationDetection.textExistsInUsersLanguage(
        firstMessageLine,
        R.string.RegistrationActivity_is_your_phone_number_above_correct,
        R.string.RegistrationActivity_edit_number
      )
    ) {
      val message: CharSequence = SpannableStringBuilder()
        .append(context.getString(firstMessageLine))
        .append("\n\n")
        .append(SpanUtil.bold(PhoneNumberFormatter.prettyPrint(e164number)))
        .append("\n\n")
        .append(context.getString(R.string.RegistrationActivity_is_your_phone_number_above_correct))

      MaterialAlertDialogBuilder(context)
        .setMessage(message)
        .setPositiveButton(android.R.string.ok) { _, _ -> onConfirmed.run() }
        .setNegativeButton(R.string.RegistrationActivity_edit_number) { _, _ -> onEditNumber.run() }
        .show()
    } else {
      onConfirmed.run()
    }
  }
}
