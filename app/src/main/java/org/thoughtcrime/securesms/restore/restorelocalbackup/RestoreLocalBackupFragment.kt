/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.restore.restorelocalbackup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.signal.core.util.bytes
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.LoggingFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.backup.BackupEvent
import org.thoughtcrime.securesms.components.ViewBinderDelegate
import org.thoughtcrime.securesms.databinding.FragmentRestoreLocalBackupBinding
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.registration.fragments.RegistrationViewDelegate.setDebugLogSubmitMultiTapView
import org.thoughtcrime.securesms.restore.RestoreActivity
import org.thoughtcrime.securesms.restore.RestoreRepository
import org.thoughtcrime.securesms.restore.RestoreViewModel
import org.thoughtcrime.securesms.util.BackupUtil
import org.thoughtcrime.securesms.util.DateUtils
import org.thoughtcrime.securesms.util.ViewModelFactory
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.visible
import java.util.Locale

/**
 * This fragment is used to monitor and manage an in-progress backup restore.
 */
class RestoreLocalBackupFragment : LoggingFragment(R.layout.fragment_restore_local_backup) {
  private val sharedViewModel: RestoreViewModel by activityViewModels()
  private val restoreLocalBackupViewModel: RestoreLocalBackupViewModel by viewModels(
    factoryProducer = ViewModelFactory.factoryProducer {
      val fileBackupUri = sharedViewModel.getBackupFileUri()!!
      RestoreLocalBackupViewModel(fileBackupUri)
    }
  )
  private val binding: FragmentRestoreLocalBackupBinding by ViewBinderDelegate(FragmentRestoreLocalBackupBinding::bind)

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    setDebugLogSubmitMultiTapView(binding.verifyHeader)
    Log.i(TAG, "Backup restore.")

    if (sharedViewModel.getBackupFileUri() == null) {
      Log.i(TAG, "No backup URI found, must navigate back to choose one.")
      findNavController().navigateUp()
      return
    }

    binding.restoreButton.setOnClickListener { presentBackupPassPhrasePromptDialog() }

    binding.cancelLocalRestoreButton.setOnClickListener {
      Log.i(TAG, "Cancel clicked.")
      findNavController().navigateUp()
    }

    if (SignalStore.settings.isBackupEnabled) {
      Log.i(TAG, "Backups enabled, so a backup must have been previously restored.")
      onBackupCompletedSuccessfully()
      return
    }

    restoreLocalBackupViewModel.backupReadError.observe(viewLifecycleOwner) { fileState ->
      fileState?.let {
        restoreLocalBackupViewModel.clearBackupFileStateError()
        handleBackupFileStateError(it)
      }
    }

    restoreLocalBackupViewModel.uiState.observe(viewLifecycleOwner) { fragmentState ->
      fragmentState.backupInfo?.let {
        presentBackupFileInfo(backupSize = it.size, backupTimestamp = it.timestamp)
      }

      if (fragmentState.restoreInProgress) {
        presentRestoreProgress(fragmentState.backupProgressCount)
      } else {
        presentProgressEnded()
      }
    }

    restoreLocalBackupViewModel.importResult.observe(viewLifecycleOwner) { importResult ->
      when (importResult) {
        null -> Unit
        RestoreRepository.BackupImportResult.SUCCESS -> onBackupCompletedSuccessfully()
        else -> {
          handleBackupImportError(importResult)
          restoreLocalBackupViewModel.backupImportErrorShown()
        }
      }
    }

    restoreLocalBackupViewModel.prepareRestore(requireContext())
  }

  private fun onBackupCompletedSuccessfully() {
    Log.d(TAG, "onBackupCompletedSuccessfully()")
    val activity = requireActivity() as RestoreActivity
    activity.onBackupCompletedSuccessfully()
  }

  override fun onStart() {
    super.onStart()
    EventBus.getDefault().register(this)
  }

  override fun onStop() {
    super.onStop()
    EventBus.getDefault().unregister(this)
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun onEvent(event: BackupEvent) {
    restoreLocalBackupViewModel.onBackupProgressUpdate(event)
  }

  private fun handleBackupFileStateError(fileState: BackupUtil.BackupFileState) {
    @StringRes
    val errorResId: Int = when (fileState) {
      BackupUtil.BackupFileState.READABLE -> throw AssertionError("Unexpected error state.")
      BackupUtil.BackupFileState.NOT_FOUND -> R.string.RestoreBackupFragment__backup_not_found
      BackupUtil.BackupFileState.NOT_READABLE -> R.string.RestoreBackupFragment__backup_has_a_bad_extension
      BackupUtil.BackupFileState.UNSUPPORTED_FILE_EXTENSION -> R.string.RestoreBackupFragment__backup_could_not_be_read
    }

    Toast.makeText(requireContext(), errorResId, Toast.LENGTH_LONG).show()
  }

  private fun handleBackupImportError(importResult: RestoreRepository.BackupImportResult) {
    when (importResult) {
      RestoreRepository.BackupImportResult.FAILURE_VERSION_DOWNGRADE -> {
        Log.i(TAG, "Notifying user of restore failure due to version downgrade.")
        Toast.makeText(requireContext(), R.string.RegistrationActivity_backup_failure_downgrade, Toast.LENGTH_LONG).show()
      }

      RestoreRepository.BackupImportResult.FAILURE_FOREIGN_KEY -> {
        Log.i(TAG, "Notifying user of restore failure due to foreign key.")
        Toast.makeText(requireContext(), R.string.RegistrationActivity_backup_failure_foreign_key, Toast.LENGTH_LONG).show()
      }

      RestoreRepository.BackupImportResult.FAILURE_UNKNOWN -> {
        Log.i(TAG, "Notifying user of restore failure due to incorrect passphrase.")
        Toast.makeText(requireContext(), R.string.RegistrationActivity_incorrect_backup_passphrase, Toast.LENGTH_LONG).show()
      }

      RestoreRepository.BackupImportResult.SUCCESS -> {
        Log.w(TAG, "Successful backup import should not be handled in this function.", IllegalStateException())
      }
    }
  }

  private fun presentProgressEnded() {
    binding.restoreButton.cancelSpinning()
    binding.cancelLocalRestoreButton.visible = true
    binding.backupProgressText.text = null
  }

  private fun presentRestoreProgress(backupProgressCount: Long) {
    binding.restoreButton.setSpinning()
    binding.cancelLocalRestoreButton.visibility = View.INVISIBLE
    if (backupProgressCount > 0L) {
      binding.backupProgressText.text = getString(R.string.RegistrationActivity_d_messages_so_far, backupProgressCount)
    } else {
      binding.backupProgressText.setText(R.string.RegistrationActivity_checking)
    }
  }

  private fun presentBackupPassPhrasePromptDialog() {
    val view = LayoutInflater.from(requireContext()).inflate(R.layout.enter_backup_passphrase_dialog, null)
    val prompt = view.findViewById<EditText>(R.id.restore_passphrase_input)

    prompt.addTextChangedListener(PassphraseAsYouTypeFormatter())

    val alertDialog = MaterialAlertDialogBuilder(requireContext())
      .setTitle(R.string.RegistrationActivity_enter_backup_passphrase)
      .setView(view)
      .setPositiveButton(R.string.RegistrationActivity_restore) { _, _ ->
        // Do nothing, we'll handle this in the setOnShowListener method below.
      }
      .setNegativeButton(android.R.string.cancel, null)
      .create()

    alertDialog.setOnShowListener { dialog ->
      val positiveButton = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE)
      positiveButton.isEnabled = false

      prompt.doOnTextChanged { text, _, _, _ ->
        val input = text.toString()
        positiveButton.isEnabled = input.isNotBlank()
      }

      positiveButton.setOnClickListener {
        ViewUtil.hideKeyboard(requireContext(), prompt)
        restoreLocalBackupViewModel.confirmPassphraseAndBeginRestore(requireContext(), prompt.text.toString())
        dialog.dismiss()
      }
    }
    alertDialog.show()

    Log.i(TAG, "Prompt for backup passphrase shown to user.")
  }

  private fun presentBackupFileInfo(backupSize: Long, backupTimestamp: Long) {
    if (backupSize > 0) {
      binding.backupSizeText.text = getString(R.string.RegistrationActivity_backup_size_s, backupSize.bytes.toUnitString())
    }

    if (backupTimestamp > 0) {
      binding.backupCreatedText.text = getString(R.string.RegistrationActivity_backup_timestamp_s, DateUtils.getExtendedRelativeTimeSpanString(requireContext(), Locale.getDefault(), backupTimestamp))
    }
  }

  companion object {
    private val TAG = Log.tag(RestoreLocalBackupFragment::class.java)
  }
}
