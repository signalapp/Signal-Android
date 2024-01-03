/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import org.signal.core.util.ResourceUtil
import org.signal.core.util.concurrent.LifecycleDisposable
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.databinding.PromptLogsBottomSheetBinding
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.BottomSheetUtil
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.NetworkUtil
import org.thoughtcrime.securesms.util.SupportEmailUtil

class DebugLogsPromptDialogFragment : FixedRoundedCornerBottomSheetDialogFragment() {

  companion object {
    private const val KEY_PURPOSE = "purpose"

    @JvmStatic
    fun show(activity: AppCompatActivity, purpose: Purpose) {
      if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
        return
      }

      if (NetworkUtil.isConnected(activity) && activity.supportFragmentManager.findFragmentByTag(BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG) == null) {
        DebugLogsPromptDialogFragment().apply {
          arguments = bundleOf(
            KEY_PURPOSE to purpose.serialized
          )
        }.show(activity.supportFragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)

        when (purpose) {
          Purpose.NOTIFICATIONS -> SignalStore.uiHints().lastNotificationLogsPrompt = System.currentTimeMillis()
          Purpose.CRASH -> SignalStore.uiHints().lastCrashPrompt = System.currentTimeMillis()
        }
      }
    }
  }

  override val peekHeightPercentage: Float = 0.66f
  override val themeResId: Int = R.style.Widget_Signal_FixedRoundedCorners_Messages

  private val binding by ViewBinderDelegate(PromptLogsBottomSheetBinding::bind)

  private val viewModel: PromptLogsViewModel by viewModels(
    factoryProducer = {
      val purpose = Purpose.deserialize(requireArguments().getInt(KEY_PURPOSE))
      PromptLogsViewModel.Factory(ApplicationDependencies.getApplication(), purpose)
    }
  )

  private val disposables: LifecycleDisposable = LifecycleDisposable()

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    return inflater.inflate(R.layout.prompt_logs_bottom_sheet, container, false)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    disposables.bindTo(viewLifecycleOwner)

    val purpose = Purpose.deserialize(requireArguments().getInt(KEY_PURPOSE))

    when (purpose) {
      Purpose.NOTIFICATIONS -> {
        binding.title.setText(R.string.PromptLogsSlowNotificationsDialog__title)
      }
      Purpose.CRASH -> {
        binding.title.setText(R.string.PromptLogsSlowNotificationsDialog__title_crash)
      }
    }

    binding.submit.setOnClickListener {
      val progressDialog = SignalProgressDialog.show(requireContext())
      disposables += viewModel.submitLogs().subscribe({ result ->
        submitLogs(result, purpose)
        progressDialog.dismiss()
        dismissAllowingStateLoss()
      }, { _ ->
        Toast.makeText(requireContext(), getString(R.string.HelpFragment__could_not_upload_logs), Toast.LENGTH_LONG).show()
        progressDialog.dismiss()
        dismissAllowingStateLoss()
      })
    }

    binding.decline.setOnClickListener {
      if (purpose == Purpose.NOTIFICATIONS) {
        SignalStore.uiHints().markDeclinedShareNotificationLogs()
      }

      dismissAllowingStateLoss()
    }
  }

  override fun onStart() {
    super.onStart()
    viewModel.onVisible()
  }

  private fun submitLogs(debugLog: String, purpose: Purpose) {
    CommunicationActions.openEmail(
      requireContext(),
      SupportEmailUtil.getSupportEmailAddress(requireContext()),
      getString(R.string.DebugLogsPromptDialogFragment__signal_android_support_request),
      getEmailBody(debugLog, purpose)
    )
  }

  private fun getEmailBody(debugLog: String?, purpose: Purpose): String {
    val suffix = StringBuilder()

    if (debugLog != null) {
      suffix.append("\n")
      suffix.append(getString(R.string.HelpFragment__debug_log))
      suffix.append(" ")
      suffix.append(debugLog)
    }

    val category = when (purpose) {
      Purpose.NOTIFICATIONS -> ResourceUtil.getEnglishResources(requireContext()).getString(R.string.DebugLogsPromptDialogFragment__slow_notifications_category)
      Purpose.CRASH -> ResourceUtil.getEnglishResources(requireContext()).getString(R.string.DebugLogsPromptDialogFragment__crash_category)
    }

    return SupportEmailUtil.generateSupportEmailBody(
      requireContext(),
      R.string.DebugLogsPromptDialogFragment__signal_android_support_request,
      " - $category",
      "\n\n",
      suffix.toString()
    )
  }

  enum class Purpose(val serialized: Int) {

    NOTIFICATIONS(1), CRASH(2);

    companion object {
      fun deserialize(serialized: Int): Purpose {
        for (value in values()) {
          if (value.serialized == serialized) {
            return value
          }
        }

        throw IllegalArgumentException("Invalid value: $serialized")
      }
    }
  }
}
