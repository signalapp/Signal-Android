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
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.LoggingFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.backup.BackupEvent
import org.thoughtcrime.securesms.components.ViewBinderDelegate
import org.thoughtcrime.securesms.databinding.FragmentRestoreLocalBackupV2Binding
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.registration.fragments.RegistrationViewDelegate.setDebugLogSubmitMultiTapView
import org.thoughtcrime.securesms.registration.fragments.RestoreBackupFragment.PassphraseAsYouTypeFormatter
import org.thoughtcrime.securesms.restore.RestoreRepository
import org.thoughtcrime.securesms.restore.RestoreViewModel
import org.thoughtcrime.securesms.util.DateUtils
import org.thoughtcrime.securesms.util.Util
import org.thoughtcrime.securesms.util.ViewModelFactory
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.visible
import java.util.Locale

/**
 * This fragment is used to monitor and manage an in-progress backup restore.
 */
class RestoreLocalBackupFragment : LoggingFragment(R.layout.fragment_restore_local_backup_v2) {
  private val navigationViewModel: RestoreViewModel by activityViewModels()
  private val restoreLocalBackupViewModel: RestoreLocalBackupViewModel by viewModels(
    factoryProducer = ViewModelFactory.factoryProducer {
      val fileBackupUri = navigationViewModel.getBackupFileUri()!!
      RestoreLocalBackupViewModel(fileBackupUri)
    }
  )
  private val binding: FragmentRestoreLocalBackupV2Binding by ViewBinderDelegate(FragmentRestoreLocalBackupV2Binding::bind)

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    setDebugLogSubmitMultiTapView(binding.verifyHeader)
    Log.i(TAG, "Backup restore.")

    restoreLocalBackupViewModel.uiState.observe(viewLifecycleOwner) { fragmentState ->
      fragmentState.backupInfo?.let {
        presentBackupFileInfo(backupSize = it.size, backupTimestamp = it.timestamp)
        if (fragmentState.backupPassphrase.isEmpty()) {
          presentBackupPassPhrasePromptDialog()
        }
      }

      if (fragmentState.restoreInProgress) {
        presentRestoreProgress(fragmentState.backupProgressCount)
      } else {
        presentProgressEnded()
      }

      if (fragmentState.backupRestoreComplete) {
        val importResult = fragmentState.backupImportResult
        if (importResult == null) {
          onBackupCompletedSuccessfully()
        } else {
          handleBackupImportResult(importResult)
        }
      }
    }

    restoreLocalBackupViewModel.startRestore(requireContext())
  }

  private fun onBackupCompletedSuccessfully() {
    Log.d(TAG, "onBackupCompletedSuccessfully()")
    SignalStore.internalValues().setForceEnterRestoreV2Flow(false)
    val activity = requireActivity()
    navigationViewModel.getNextIntent()?.let {
      Log.d(TAG, "Launching ${it.component}")
      activity.startActivity(it)
    }
    activity.finish()
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

  private fun handleBackupImportResult(importResult: RestoreRepository.BackupImportResult) {
    when (importResult) {
      RestoreRepository.BackupImportResult.FAILURE_VERSION_DOWNGRADE -> Toast.makeText(requireContext(), R.string.RegistrationActivity_backup_failure_downgrade, Toast.LENGTH_LONG).show()
      RestoreRepository.BackupImportResult.FAILURE_FOREIGN_KEY -> Toast.makeText(requireContext(), R.string.RegistrationActivity_backup_failure_foreign_key, Toast.LENGTH_LONG).show()
      RestoreRepository.BackupImportResult.FAILURE_UNKNOWN -> Toast.makeText(requireContext(), R.string.RegistrationActivity_incorrect_backup_passphrase, Toast.LENGTH_LONG).show()
      RestoreRepository.BackupImportResult.SUCCESS -> Log.w(TAG, "Successful backup import should not be handled here.", IllegalStateException())
    }
  }

  private fun presentProgressEnded() {
    binding.restoreButton.cancelSpinning()
    binding.cancelLocalRestoreButton.visible = true
    binding.backupProgressText.text = ""
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

    MaterialAlertDialogBuilder(requireContext())
      .setTitle(R.string.RegistrationActivity_enter_backup_passphrase)
      .setView(view)
      .setPositiveButton(R.string.RegistrationActivity_restore) { _, _ ->
        ViewUtil.hideKeyboard(requireContext(), prompt)

        val passphrase = prompt.getText().toString()
        restoreLocalBackupViewModel.confirmPassphrase(requireContext(), passphrase)
      }
      .setNegativeButton(android.R.string.cancel, null)
      .show()

    Log.i(TAG, "Prompt for backup passphrase shown to user.")
  }

  private fun presentBackupFileInfo(backupSize: Long, backupTimestamp: Long) {
    if (backupSize > 0) {
      binding.backupSizeText.text = getString(R.string.RegistrationActivity_backup_size_s, Util.getPrettyFileSize(backupSize))
    }

    if (backupTimestamp > 0) {
      binding.backupCreatedText.text = getString(R.string.RegistrationActivity_backup_timestamp_s, DateUtils.getExtendedRelativeTimeSpanString(requireContext(), Locale.getDefault(), backupTimestamp))
    }
  }

  companion object {
    private val TAG = Log.tag(RestoreLocalBackupFragment::class.java)
  }
}
