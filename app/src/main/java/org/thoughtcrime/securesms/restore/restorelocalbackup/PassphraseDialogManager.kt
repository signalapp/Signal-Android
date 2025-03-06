/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.restore.restorelocalbackup

import android.content.Context
import android.view.LayoutInflater
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doOnTextChanged
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.ViewUtil

class PassphraseDialogManager(private val context: Context) {

  fun showDialog(action: (String) -> Unit) {
    createDialog(action).show()
  }

  private fun createDialog(action: (String) -> Unit): AlertDialog {
    val view = LayoutInflater.from(context).inflate(R.layout.enter_backup_passphrase_dialog, null)
    val passphraseInputLayout = view.findViewById<TextInputLayout>(R.id.restore_passphrase_input_layout)
    val prompt = view.findViewById<EditText>(R.id.restore_passphrase_input)

    prompt.addTextChangedListener(PassphraseAsYouTypeFormatter())

    val alertDialog = MaterialAlertDialogBuilder(context)
      .setTitle(R.string.RegistrationActivity_enter_backup_passphrase)
      .setView(view)
      .setPositiveButton(R.string.RegistrationActivity_restore) { _, _ ->
        // Do nothing, we'll handle this in the onShowListener
      }
      .setNegativeButton(android.R.string.cancel, null)
      .create()

    alertDialog.setOnShowListener { dialog ->
      val positiveButton = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE)
      positiveButton.isEnabled = false

      prompt.doOnTextChanged { text, _, _, _ ->
        val input = text.toString()
        if (input.isBlank()) {
          positiveButton.isEnabled = false
        } else {
          passphraseInputLayout.error = null
          positiveButton.isEnabled = true
        }
      }

      positiveButton.setOnClickListener {
        ViewUtil.hideKeyboard(context, prompt)
        action(prompt.text.toString())
        dialog.dismiss()
      }
    }
    return alertDialog
  }
}