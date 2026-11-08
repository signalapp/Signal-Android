/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.devicetransfer

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.annotation.StringRes
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.signal.core.ui.logging.LoggingFragment
import org.signal.core.util.eventbus.EventBus
import org.signal.core.util.eventbus.RestartableSubscription
import org.signal.core.util.eventbus.ThreadMode
import org.signal.devicetransfer.DeviceToDeviceTransferService
import org.signal.devicetransfer.TransferStatus
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.ViewBinderDelegate
import org.thoughtcrime.securesms.databinding.FragmentDeviceTransferBinding
import org.thoughtcrime.securesms.util.SystemWindowInsetsSetter
import org.thoughtcrime.securesms.util.visible

/**
 * Drives the UI for the actual device transfer progress. Shown after setup is complete
 * and the two devices are transferring.
 * <p>
 * Handles show progress and error state.
 */
abstract class DeviceTransferFragment : LoggingFragment(R.layout.fragment_device_transfer) {

  companion object {
    private const val TRANSFER_FINISHED_KEY = "transfer_finished"
  }

  private val onBackPressed = OnBackPressed()
  private val transferStatusSubscription = RestartableSubscription {
    EventBus.default.subscribeSticky<TransferStatus>(ThreadMode.MAIN) { event ->
      if (event.transferMode != TransferStatus.TransferMode.SERVICE_CONNECTED) {
        abort()
      }
    }
  }
  protected val binding: FragmentDeviceTransferBinding by ViewBinderDelegate(FragmentDeviceTransferBinding::bind)
  protected val status: TextView by lazy { binding.deviceTransferFragmentStatus }

  protected var transferFinished: Boolean = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (savedInstanceState != null) {
      transferFinished = savedInstanceState.getBoolean(TRANSFER_FINISHED_KEY)
    }
  }

  override fun onStart() {
    super.onStart()
    if (transferFinished) {
      navigateToTransferComplete()
    }
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    outState.putBoolean(TRANSFER_FINISHED_KEY, transferFinished)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    SystemWindowInsetsSetter.attach(view, viewLifecycleOwner)
    requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

    binding.deviceTransferFragmentCancel.setOnClickListener {
      cancelActiveTransfer()
    }
    binding.deviceTransferFragmentTryAgain.setOnClickListener {
      ignoreTransferStatusEvents()
      EventBus.default.removeStickyEvent<TransferStatus>()
      navigateToRestartTransfer()
    }

    transferStatusSubscription.start()

    requireActivity().onBackPressedDispatcher.addCallback(
      viewLifecycleOwner,
      onBackPressed
    )
  }

  override fun onDestroyView() {
    requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    ignoreTransferStatusEvents()
    super.onDestroyView()
  }

  private fun cancelActiveTransfer() {
    MaterialAlertDialogBuilder(requireContext()).apply {
      setTitle(R.string.DeviceTransfer__stop_transfer)
      setMessage(R.string.DeviceTransfer__all_transfer_progress_will_be_lost)
      setPositiveButton(R.string.DeviceTransfer__stop_transfer) { _, _ ->
        ignoreTransferStatusEvents()
        DeviceToDeviceTransferService.stop(requireContext())
        EventBus.default.removeStickyEvent<TransferStatus>()
        navigateAwayFromTransfer()
      }
      setNegativeButton(android.R.string.cancel, null)
    }
      .show()
  }

  protected fun ignoreTransferStatusEvents() {
    transferStatusSubscription.stop()
  }

  protected abstract fun navigateToRestartTransfer()

  protected abstract fun navigateAwayFromTransfer()

  protected abstract fun navigateToTransferComplete()

  protected fun abort() {
    abort(R.string.DeviceTransfer__transfer_failed)
  }

  protected fun abort(@StringRes errorMessage: Int) {
    ignoreTransferStatusEvents()
    DeviceToDeviceTransferService.stop(requireContext())

    binding.deviceTransferFragmentProgress.visible = false
    binding.deviceTransferFragmentAlert.visible = true
    binding.deviceTransferFragmentTryAgain.visible = true

    binding.deviceTransferFragmentTitle.setText(R.string.DeviceTransfer__unable_to_transfer)
    binding.deviceTransferFragmentStatus.setText(errorMessage)
    binding.deviceTransferFragmentCancel.setText(R.string.DeviceTransfer__cancel)
    binding.deviceTransferFragmentCancel.setOnClickListener { navigateAwayFromTransfer() }

    onBackPressed.isActiveTransfer = false
  }

  protected inner class OnBackPressed : OnBackPressedCallback(true) {
    internal var isActiveTransfer = true

    override fun handleOnBackPressed() {
      if (isActiveTransfer) {
        cancelActiveTransfer()
      } else {
        navigateAwayFromTransfer()
      }
    }
  }
}
