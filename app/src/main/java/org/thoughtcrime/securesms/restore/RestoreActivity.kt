/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.restore

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.signal.core.util.ThreadUtil
import org.signal.core.util.getParcelableExtraCompat
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.BaseActivity
import org.thoughtcrime.securesms.MainActivity
import org.thoughtcrime.securesms.PassphraseRequiredActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.RestoreDirections
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.keyvalue.isWantingManualRemoteRestore
import org.thoughtcrime.securesms.registration.ui.restore.RemoteRestoreActivity
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Activity to hold the restore from backup flow.
 */
class RestoreActivity : BaseActivity() {

  private val dynamicTheme = DynamicNoActionBarTheme()
  private val sharedViewModel: RestoreViewModel by viewModels()

  private lateinit var navController: NavController

  override fun onCreate(savedInstanceState: Bundle?) {
    dynamicTheme.onCreate(this)
    super.onCreate(savedInstanceState)

    setResult(RESULT_CANCELED)

    setContentView(R.layout.activity_restore)

    if (savedInstanceState == null) {
      val fragment: NavHostFragment = NavHostFragment.create(R.navigation.restore)

      supportFragmentManager
        .beginTransaction()
        .replace(R.id.nav_host_fragment, fragment)
        .commitNow()

      navController = fragment.navController
    } else {
      val fragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
      navController = fragment.navController
    }

    intent.getParcelableExtraCompat(PassphraseRequiredActivity.NEXT_INTENT_EXTRA, Intent::class.java)?.let {
      sharedViewModel.setNextIntent(it)
    }

    val navTarget = NavTarget.deserialize(intent.getIntExtra(EXTRA_NAV_TARGET, NavTarget.NEW_LANDING.value))

    when (navTarget) {
      NavTarget.NEW_LANDING -> {
        if (sharedViewModel.hasNoRestoreMethods()) {
          if (SignalStore.registration.restoreDecisionState.isWantingManualRemoteRestore) {
            Log.i(TAG, "User has no available restore methods but previously wanted a remote restore, navigating immediately.")
            startActivity(RemoteRestoreActivity.getIntent(this, isOnlyOption = true))
          } else {
            Log.i(TAG, "No restore methods available, skipping")
            sharedViewModel.skipRestore()

            val nextIntent = sharedViewModel.getNextIntent()

            if (nextIntent != null) {
              Log.d(TAG, "Launching ${nextIntent.component}")
              startActivity(nextIntent)
            } else {
              startActivity(MainActivity.clearTop(this))
            }
          }

          supportFinishAfterTransition()
        }
      }

      NavTarget.LOCAL_RESTORE -> navController.safeNavigate(RestoreDirections.goDirectlyToChooseLocalBackup())
      NavTarget.TRANSFER -> navController.safeNavigate(RestoreDirections.goDirectlyToDeviceTransfer())
    }

    onBackPressedDispatcher.addCallback(
      this,
      object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
          onNavigateUp()
        }
      }
    )

    lifecycleScope.launch {
      lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
        while (isActive) {
          if (TextSecurePreferences.isUnauthorizedReceived(this@RestoreActivity)) {
            ThreadUtil.runOnMain { showUnregisteredDialog() }
            break
          }
          delay(1000)
        }
      }
    }
  }

  override fun onResume() {
    super.onResume()
    dynamicTheme.onResume(this)
  }

  override fun onNavigateUp(): Boolean {
    return if (!Navigation.findNavController(this, R.id.nav_host_fragment).popBackStack()) {
      finish()
      true
    } else {
      false
    }
  }

  fun onBackupCompletedSuccessfully() {
    sharedViewModel.getNextIntent()?.let {
      Log.d(TAG, "Launching ${it.component}")
      startActivity(it)
    }

    setResult(RESULT_OK)
    finish()
  }

  private fun showUnregisteredDialog() {
    MaterialAlertDialogBuilder(this)
      .setTitle(R.string.RestoreActivity__no_longer_registered_title)
      .setMessage(R.string.RestoreActivity__no_longer_registered_message)
      .setCancelable(false)
      .setPositiveButton(android.R.string.ok) { _, _ ->
        sharedViewModel.skipRestore()
        startActivity(MainActivity.clearTop(this))
        supportFinishAfterTransition()
      }
      .show()
  }

  companion object {

    private val TAG = Log.tag(RestoreActivity::class)

    enum class NavTarget(val value: Int) {
      NEW_LANDING(1),
      TRANSFER(2),
      LOCAL_RESTORE(3);

      companion object {
        fun deserialize(value: Int): NavTarget {
          return entries.firstOrNull { it.value == value } ?: NEW_LANDING
        }
      }
    }

    private const val EXTRA_NAV_TARGET = "nav_target"

    @JvmStatic
    fun getLocalRestoreIntent(context: Context): Intent {
      return Intent(context, RestoreActivity::class.java).apply {
        putExtra(EXTRA_NAV_TARGET, NavTarget.LOCAL_RESTORE.value)
      }
    }

    @JvmStatic
    fun getRestoreIntent(context: Context): Intent {
      return Intent(context, RestoreActivity::class.java)
    }
  }
}
