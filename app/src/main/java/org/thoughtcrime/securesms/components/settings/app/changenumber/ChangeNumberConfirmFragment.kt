/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.changenumber

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.LoggingFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Screen visible to the user for them to confirm their new phone number was entered correctly.
 */
class ChangeNumberConfirmFragment : LoggingFragment(R.layout.fragment_change_number_confirm) {

  companion object {
    private val TAG = Log.tag(ChangeNumberConfirmFragment::class.java)
  }

  private val viewModel by activityViewModels<ChangeNumberViewModel>()

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    val toolbar: Toolbar = view.findViewById(R.id.toolbar)
    toolbar.setTitle(R.string.ChangeNumberEnterPhoneNumberFragment__change_number)
    toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

    val confirmMessage: TextView = view.findViewById(R.id.change_number_confirm_new_number_message)
    confirmMessage.text = getString(R.string.ChangeNumberConfirmFragment__you_are_about_to_change_your_phone_number_from_s_to_s, viewModel.oldNumberState.fullFormattedNumber, viewModel.number.fullFormattedNumber)

    val newNumber: TextView = view.findViewById(R.id.change_number_confirm_new_number)
    newNumber.text = viewModel.number.fullFormattedNumber

    val editNumber: View = view.findViewById(R.id.change_number_confirm_edit_number)
    editNumber.setOnClickListener { findNavController().navigateUp() }

    val changeNumber: View = view.findViewById(R.id.change_number_confirm_change_number)
    changeNumber.setOnClickListener {
      viewModel.registerSmsListenerWithCompletionListener(requireContext()) {
        navigateToVerify(it)
      }
    }
  }

  private fun navigateToVerify(smsListenerEnabled: Boolean = false) {
    findNavController().safeNavigate(ChangeNumberConfirmFragmentDirections.actionChangePhoneNumberConfirmFragmentToChangePhoneNumberVerifyFragment(smsListenerEnabled))
  }
}
