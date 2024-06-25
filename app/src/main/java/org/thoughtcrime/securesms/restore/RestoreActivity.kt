/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.restore

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.navigation.findNavController
import org.signal.core.util.getParcelableExtraCompat
import org.thoughtcrime.securesms.BaseActivity
import org.thoughtcrime.securesms.PassphraseRequiredActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.registration.ui.restore.RemoteRestoreActivity
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme

/**
 * Activity to hold the restore from backup flow.
 */
class RestoreActivity : BaseActivity() {

  private val dynamicTheme = DynamicNoActionBarTheme()
  private val sharedViewModel: RestoreViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    dynamicTheme.onCreate(this)
    super.onCreate(savedInstanceState)

    setResult(RESULT_CANCELED)

    setContentView(R.layout.activity_restore)
    intent.getParcelableExtraCompat(PassphraseRequiredActivity.NEXT_INTENT_EXTRA, Intent::class.java)?.let {
      sharedViewModel.setNextIntent(it)
    }

    val navTarget = NavTarget.deserialize(intent.getIntExtra(EXTRA_NAV_TARGET, NavTarget.NONE.value))
    when (navTarget) {
      NavTarget.LOCAL_RESTORE -> findNavController(R.id.nav_host_fragment).navigate(R.id.choose_local_backup_fragment)
      NavTarget.TRANSFER -> findNavController(R.id.nav_host_fragment).navigate(R.id.newDeviceTransferInstructions)
      else -> Unit
    }
  }

  override fun onResume() {
    super.onResume()
    dynamicTheme.onResume(this)
  }

  fun finishActivitySuccessfully() {
    setResult(RESULT_OK)
    finish()
  }

  companion object {

    enum class NavTarget(val value: Int) {
      NONE(0),
      TRANSFER(1),
      LOCAL_RESTORE(2);

      companion object {
        fun deserialize(value: Int): NavTarget {
          return values().firstOrNull { it.value == value } ?: NONE
        }
      }
    }

    private const val EXTRA_NAV_TARGET = "nav_target"

    @JvmStatic
    fun getIntentForTransfer(context: Context): Intent {
      return Intent(context, RestoreActivity::class.java).apply {
        putExtra(EXTRA_NAV_TARGET, NavTarget.TRANSFER.value)
      }
    }

    @JvmStatic
    fun getIntentForLocalRestore(context: Context): Intent {
      return Intent(context, RestoreActivity::class.java).apply {
        putExtra(EXTRA_NAV_TARGET, NavTarget.LOCAL_RESTORE.value)
      }
    }

    @JvmStatic
    fun getIntentForTransferOrRestore(context: Context): Intent {
      val tier = SignalStore.backup.backupTier
      if (tier == MessageBackupTier.PAID) {
        return Intent(context, RemoteRestoreActivity::class.java)
      }
      return Intent(context, RestoreActivity::class.java)
    }
  }
}
