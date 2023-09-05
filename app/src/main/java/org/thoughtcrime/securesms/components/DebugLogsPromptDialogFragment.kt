/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModelProvider
import org.signal.core.util.ResourceUtil
import org.signal.core.util.concurrent.LifecycleDisposable
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.databinding.PromptLogsBottomSheetBinding
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.BottomSheetUtil
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.NetworkUtil
import org.thoughtcrime.securesms.util.SupportEmailUtil

class DebugLogsPromptDialogFragment : FixedRoundedCornerBottomSheetDialogFragment() {

  companion object {

    @JvmStatic
    fun show(context: Context, fragmentManager: FragmentManager) {
      if (NetworkUtil.isConnected(context) && fragmentManager.findFragmentByTag(BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG) == null) {
        DebugLogsPromptDialogFragment().apply {
          arguments = bundleOf()
        }.show(fragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
        SignalStore.uiHints().lastNotificationLogsPrompt = System.currentTimeMillis()
      }
    }
  }

  override val peekHeightPercentage: Float = 0.66f
  override val themeResId: Int = R.style.Widget_Signal_FixedRoundedCorners_Messages

  private val binding by ViewBinderDelegate(PromptLogsBottomSheetBinding::bind)

  private lateinit var viewModel: PromptLogsViewModel

  private val disposables: LifecycleDisposable = LifecycleDisposable()

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    return inflater.inflate(R.layout.prompt_logs_bottom_sheet, container, false)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    disposables.bindTo(viewLifecycleOwner)

    viewModel = ViewModelProvider(this).get(PromptLogsViewModel::class.java)
    binding.submit.setOnClickListener {
      val progressDialog = SignalProgressDialog.show(requireContext())
      disposables += viewModel.submitLogs().subscribe({ result ->
        submitLogs(result)
        progressDialog.dismiss()
        dismiss()
      }, { _ ->
        Toast.makeText(requireContext(), getString(R.string.HelpFragment__could_not_upload_logs), Toast.LENGTH_LONG).show()
        progressDialog.dismiss()
        dismiss()
      })
    }
    binding.decline.setOnClickListener {
      SignalStore.uiHints().markDeclinedShareNotificationLogs()
      dismiss()
    }
  }

  private fun submitLogs(debugLog: String) {
    CommunicationActions.openEmail(
      requireContext(),
      SupportEmailUtil.getSupportEmailAddress(requireContext()),
      getString(R.string.DebugLogsPromptDialogFragment__signal_android_support_request),
      getEmailBody(debugLog)
    )
  }

  private fun getEmailBody(debugLog: String?): String {
    val suffix = StringBuilder()
    if (debugLog != null) {
      suffix.append("\n")
      suffix.append(getString(R.string.HelpFragment__debug_log))
      suffix.append(" ")
      suffix.append(debugLog)
    }
    val category = ResourceUtil.getEnglishResources(requireContext()).getString(R.string.DebugLogsPromptDialogFragment__slow_notifications_category)
    return SupportEmailUtil.generateSupportEmailBody(
      requireContext(),
      R.string.DebugLogsPromptDialogFragment__signal_android_support_request,
      " - $category",
      "\n\n",
      suffix.toString()
    )
  }
}
