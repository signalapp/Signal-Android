/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.accountlocked

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.activityViewModels
import org.thoughtcrime.securesms.LoggingFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.registration.fragments.RegistrationViewDelegate.setDebugLogSubmitMultiTapView
import org.thoughtcrime.securesms.registration.ui.RegistrationViewModel
import kotlin.time.Duration.Companion.milliseconds

/**
 * Screen educating the user that they need to wait some number of days to register.
 */
class AccountLockedFragment : LoggingFragment(R.layout.account_locked_fragment) {
  private val viewModel by activityViewModels<RegistrationViewModel>()
  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    setDebugLogSubmitMultiTapView(view.findViewById(R.id.account_locked_title))

    val description = view.findViewById<TextView>(R.id.account_locked_description)

    viewModel.lockedTimeRemaining.observe(
      viewLifecycleOwner
    ) { t: Long? -> description.text = getString(R.string.AccountLockedFragment__your_account_has_been_locked_to_protect_your_privacy, durationToDays(t!!)) }

    view.findViewById<View>(R.id.account_locked_next).setOnClickListener { v: View? -> onNext() }
    view.findViewById<View>(R.id.account_locked_learn_more).setOnClickListener { v: View? -> learnMore() }

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

  fun onNext() {
    requireActivity().finish()
  }

  private fun durationToDays(duration: Long): Long {
    return if (duration != 0L) getLockoutDays(duration).toLong() else 7
  }

  private fun getLockoutDays(timeRemainingMs: Long): Int {
    return timeRemainingMs.milliseconds.inWholeDays.toInt() + 1
  }
}
