/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.changenumber

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.MainActivity
import org.thoughtcrime.securesms.PassphraseRequiredActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.logsubmit.SubmitDebugLogActivity
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme
import org.thoughtcrime.securesms.util.DynamicTheme
import org.thoughtcrime.securesms.util.SignalE164Util

/**
 * A captive activity that can determine if an interrupted/erred change number request
 * caused a disparity between the server and our locally stored number.
 */
class ChangeNumberLockActivity : PassphraseRequiredActivity() {

  companion object {
    private val TAG: String = Log.tag(ChangeNumberLockActivity::class.java)

    @JvmStatic
    fun createIntent(context: Context): Intent {
      return Intent(context, ChangeNumberLockActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
      }
    }
  }

  private val viewModel: ChangeNumberViewModel by viewModels()
  private val dynamicTheme: DynamicTheme = DynamicNoActionBarTheme()

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    dynamicTheme.onCreate(this)

    onBackPressedDispatcher.addCallback(
      this,
      object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
          Log.d(TAG, "Back button press swallowed.")
        }
      }
    )

    setContentView(R.layout.activity_change_number_lock)

    checkWhoAmI()
  }

  override fun onResume() {
    super.onResume()
    dynamicTheme.onResume(this)
  }

  private fun checkWhoAmI() {
    viewModel.checkWhoAmI(::onChangeStatusConfirmed, ::onFailedToGetChangeNumberStatus)
  }

  private fun onChangeStatusConfirmed() {
    SignalStore.misc.clearPendingChangeNumberMetadata()

    MaterialAlertDialogBuilder(this)
      .setTitle(R.string.ChangeNumberLockActivity__change_status_confirmed)
      .setMessage(getString(R.string.ChangeNumberLockActivity__your_number_has_been_confirmed_as_s, SignalE164Util.prettyPrint(SignalStore.account.e164!!)))
      .setPositiveButton(android.R.string.ok) { _, _ ->
        startActivity(MainActivity.clearTop(this))
        finish()
      }
      .setCancelable(false)
      .show()
  }

  private fun onFailedToGetChangeNumberStatus(error: Throwable) {
    Log.w(TAG, "Unable to determine status of change number", error)

    MaterialAlertDialogBuilder(this)
      .setTitle(R.string.ChangeNumberLockActivity__change_status_unconfirmed)
      .setMessage(getString(R.string.ChangeNumberLockActivity__we_could_not_determine_the_status_of_your_change_number_request, error.javaClass.simpleName))
      .setPositiveButton(R.string.ChangeNumberLockActivity__retry) { _, _ -> checkWhoAmI() }
      .setNegativeButton(R.string.ChangeNumberLockActivity__leave) { _, _ -> finish() }
      .setNeutralButton(R.string.ChangeNumberLockActivity__submit_debug_log) { _, _ ->
        startActivity(Intent(this, SubmitDebugLogActivity::class.java))
        finish()
      }
      .setCancelable(false)
      .show()
  }
}
