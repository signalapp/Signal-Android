/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.changenumber

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.LoggingFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.registration.fragments.RegistrationViewDelegate.setDebugLogSubmitMultiTapView
import java.util.concurrent.TimeUnit

/**
 * Screen visible to the user when they are registration locked and have no SVR data.
 */
class ChangeNumberAccountLockedFragment : LoggingFragment(R.layout.fragment_change_number_account_locked) {

  companion object {
    private val TAG = Log.tag(ChangeNumberAccountLockedFragment::class.java)
  }

  private val viewModel by activityViewModels<ChangeNumberViewModel>()

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    setDebugLogSubmitMultiTapView(view.findViewById(R.id.account_locked_title))

    val description = view.findViewById<TextView>(R.id.account_locked_description)

    viewModel.liveLockedTimeRemaining.observe(viewLifecycleOwner) { t: Long ->
      description.text = getString(R.string.AccountLockedFragment__your_account_has_been_locked_to_protect_your_privacy, durationToDays(t))
    }

    view.findViewById<View>(R.id.account_locked_next).setOnClickListener { onNext() }
    view.findViewById<View>(R.id.account_locked_learn_more).setOnClickListener { learnMore() }

    requireActivity().onBackPressedDispatcher.addCallback(
      viewLifecycleOwner,
      object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
          onNext()
        }
      }
    )
  }

  private fun learnMore() {
    val intent = Intent(Intent.ACTION_VIEW)
    intent.setData(Uri.parse(getString(R.string.AccountLockedFragment__learn_more_url)))
    startActivity(intent)
  }

  private fun durationToDays(duration: Long): Long {
    return if (duration != 0L) getLockoutDays(duration).toLong() else 7
  }

  private fun getLockoutDays(timeRemainingMs: Long): Int {
    return TimeUnit.MILLISECONDS.toDays(timeRemainingMs).toInt() + 1
  }

  fun onNext() {
    findNavController().navigateUp()
  }
}
