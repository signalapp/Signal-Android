/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.restore.devicetransfer

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.annotation.StringRes
import androidx.fragment.app.activityViewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.signal.devicetransfer.DeviceToDeviceTransferService
import org.signal.devicetransfer.TransferStatus
import org.thoughtcrime.securesms.LoggingFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.ViewBinderDelegate
import org.thoughtcrime.securesms.databinding.FragmentDeviceTransferBinding
import org.thoughtcrime.securesms.restore.RestoreViewModel
import org.thoughtcrime.securesms.util.visible

sealed class DeviceTransferFragment : LoggingFragment(R.layout.fragment_device_transfer) {
  private val onBackPressed = OnBackPressed()
  private val transferModeListener = TransferModeListener()
  protected val navigationViewModel: RestoreViewModel by activityViewModels()
  protected val binding: FragmentDeviceTransferBinding by ViewBinderDelegate(FragmentDeviceTransferBinding::bind)

  protected var transferFinished: Boolean = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (savedInstanceState != null) {
      transferFinished = savedInstanceState.getBoolean(TRANSFER_FINISHED_KEY)
    }
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    outState.putBoolean(TRANSFER_FINISHED_KEY, transferFinished)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    binding.deviceTransferFragmentCancel.setOnClickListener {
      cancelActiveTransfer()
    }
    binding.deviceTransferFragmentTryAgain.setOnClickListener {
      EventBus.getDefault().unregister(transferModeListener)
      EventBus.getDefault().removeStickyEvent(TransferStatus::class.java)
      navigateToRestartTransfer()
    }

    EventBus.getDefault().register(transferModeListener)

    requireActivity().onBackPressedDispatcher.addCallback(
      viewLifecycleOwner,
      onBackPressed
    )
  }

  override fun onDestroyView() {
    EventBus.getDefault().unregister(transferModeListener)
    super.onDestroyView()
  }

  private fun cancelActiveTransfer() {
    MaterialAlertDialogBuilder(requireContext()).apply {
      setTitle(R.string.DeviceTransfer__stop_transfer)
      setMessage(R.string.DeviceTransfer__all_transfer_progress_will_be_lost)
      setPositiveButton(R.string.DeviceTransfer__stop_transfer) { _, _ ->
        EventBus.getDefault().unregister(transferModeListener)
        DeviceToDeviceTransferService.stop(requireContext())
        EventBus.getDefault().removeStickyEvent(TransferStatus::class.java)
        navigateAwayFromTransfer()
      }
      setNegativeButton(android.R.string.cancel, null)
    }
      .show()
  }

  protected fun ignoreTransferStatusEvents() {
    EventBus.getDefault().unregister(transferModeListener)
  }

  protected abstract fun navigateToRestartTransfer()

  protected abstract fun navigateAwayFromTransfer()

  protected abstract fun navigateToTransferComplete()

  protected fun abort() {
    abort(R.string.DeviceTransfer__transfer_failed)
  }

  protected fun abort(@StringRes errorMessage: Int) {
    EventBus.getDefault().unregister(transferModeListener)
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

  private inner class TransferModeListener {
    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: TransferStatus) {
      if (event.transferMode != TransferStatus.TransferMode.SERVICE_CONNECTED) {
        abort()
      }
    }
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

  companion object {
    private const val TRANSFER_FINISHED_KEY = "transfer_finished"
  }
}
