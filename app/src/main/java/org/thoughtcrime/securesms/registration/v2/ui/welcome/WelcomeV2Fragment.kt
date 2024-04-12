/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.v2.ui.welcome

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.NavHostFragment
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.LoggingFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.ViewBinderDelegate
import org.thoughtcrime.securesms.databinding.FragmentRegistrationWelcomeV2Binding
import org.thoughtcrime.securesms.permissions.Permissions
import org.thoughtcrime.securesms.registration.fragments.RegistrationViewDelegate.setDebugLogSubmitMultiTapView
import org.thoughtcrime.securesms.registration.fragments.WelcomePermissions
import org.thoughtcrime.securesms.registration.v2.ui.grantpermissions.GrantPermissionsV2Fragment
import org.thoughtcrime.securesms.registration.v2.ui.shared.RegistrationV2ViewModel
import org.thoughtcrime.securesms.util.BackupUtil
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.thoughtcrime.securesms.util.Util
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import kotlin.jvm.optionals.getOrNull

/**
 * First screen that is displayed on the very first app launch.
 */
class WelcomeV2Fragment : LoggingFragment(R.layout.fragment_registration_welcome_v2) {
  private val TAG = Log.tag(WelcomeV2Fragment::class.java)
  private val sharedViewModel by activityViewModels<RegistrationV2ViewModel>()
  private val binding: FragmentRegistrationWelcomeV2Binding by ViewBinderDelegate(FragmentRegistrationWelcomeV2Binding::bind)

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    maybePrefillE164()
    setDebugLogSubmitMultiTapView(binding.image)
    setDebugLogSubmitMultiTapView(binding.title)
    binding.welcomeContinueButton.setOnClickListener { onContinueClicked() }
    binding.welcomeTermsButton.setOnClickListener { onTermsClicked() }
    binding.welcomeTransferOrRestore.setOnClickListener { onRestoreFromBackupClicked() }
  }

  private fun onContinueClicked() {
    TextSecurePreferences.setHasSeenWelcomeScreen(requireContext(), true)
    if (Permissions.isRuntimePermissionsRequired() && !hasAllPermissions()) {
      NavHostFragment.findNavController(this).safeNavigate(WelcomeV2FragmentDirections.actionWelcomeFragmentToGrantPermissionsV2Fragment(GrantPermissionsV2Fragment.WelcomeAction.CONTINUE))
    } else {
      skipRestore()
    }
  }

  private fun hasAllPermissions(): Boolean {
    val isUserSelectionRequired = BackupUtil.isUserSelectionRequired(requireContext())
    return WelcomePermissions.getWelcomePermissions(isUserSelectionRequired).all { ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED }
  }

  private fun skipRestore() {
    NavHostFragment.findNavController(this).safeNavigate(WelcomeV2FragmentDirections.actionSkipRestore())
  }

  private fun onRestoreFromBackupClicked() {
    Toast.makeText(requireContext(), "Not yet implemented.", Toast.LENGTH_SHORT).show()
  }

  private fun onTermsClicked() {
    Toast.makeText(requireContext(), "Not yet implemented.", Toast.LENGTH_SHORT).show()
  }

  private fun maybePrefillE164() {
    if (Permissions.hasAll(requireContext(), Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_PHONE_NUMBERS)) {
      val localNumber = Util.getDeviceNumber(requireContext()).getOrNull()

      if (localNumber != null) {
        Log.v(TAG, "Phone number detected.")
        sharedViewModel.setPhoneNumber(localNumber)
      } else {
        Log.i(TAG, "Could not read phone number.")
      }
    } else {
      Log.i(TAG, "No phone permission.")
    }
  }
}
