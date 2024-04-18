/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.v2.ui.entercode

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.activityViewModels
import androidx.navigation.ActivityNavigator
import androidx.navigation.fragment.NavHostFragment
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.LoggingFragment
import org.thoughtcrime.securesms.MainActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.ViewBinderDelegate
import org.thoughtcrime.securesms.databinding.FragmentRegistrationEnterCodeV2Binding
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.lock.v2.CreateSvrPinActivity
import org.thoughtcrime.securesms.profiles.AvatarHelper
import org.thoughtcrime.securesms.profiles.edit.CreateProfileActivity
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.registration.fragments.RegistrationViewDelegate.setDebugLogSubmitMultiTapView
import org.thoughtcrime.securesms.registration.fragments.SignalStrengthPhoneStateListener
import org.thoughtcrime.securesms.registration.v2.ui.shared.RegistrationCheckpoint
import org.thoughtcrime.securesms.registration.v2.ui.shared.RegistrationV2ViewModel

/**
 * The final screen of account registration, where the user enters their verification code.
 */
class EnterCodeV2Fragment : LoggingFragment(R.layout.fragment_registration_enter_code_v2) {

  private val TAG = Log.tag(EnterCodeV2Fragment::class.java)

  private val sharedViewModel by activityViewModels<RegistrationV2ViewModel>()
  private val binding: FragmentRegistrationEnterCodeV2Binding by ViewBinderDelegate(FragmentRegistrationEnterCodeV2Binding::bind)

  private lateinit var phoneStateListener: SignalStrengthPhoneStateListener

  private var autopilotCodeEntryActive = false

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    setDebugLogSubmitMultiTapView(binding.verifyHeader)

    phoneStateListener = SignalStrengthPhoneStateListener(this, PhoneStateCallback())

    requireActivity().onBackPressedDispatcher.addCallback(
      viewLifecycleOwner,
      object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
          popBackStack()
        }
      }
    )

    binding.wrongNumber.setOnClickListener {
      popBackStack()
    }

    binding.code.setOnCompleteListener {
      sharedViewModel.verifyCodeWithoutRegistrationLock(requireContext(), it)
    }

    binding.keyboard.setOnKeyPressListener { key ->
      if (!autopilotCodeEntryActive) {
        if (key >= 0) {
          binding.code.append(key)
        } else {
          binding.code.delete()
        }
      }
    }

    sharedViewModel.uiState.observe(viewLifecycleOwner) {
      if (it.registrationCheckpoint == RegistrationCheckpoint.SERVICE_REGISTRATION_COMPLETED) {
        handleSuccessfulVerify()
      }
    }
  }

  private fun handleSuccessfulVerify() {
    // TODO [regv2]: add functionality of [RegistrationCompleteFragment]
    val activity = requireActivity()
    val isProfileNameEmpty = Recipient.self().profileName.isEmpty
    val isAvatarEmpty = !AvatarHelper.hasAvatar(activity, Recipient.self().id)
    val needsProfile = isProfileNameEmpty || isAvatarEmpty
    val needsPin = !sharedViewModel.hasPin()

    Log.i(TAG, "Pin restore flow not required. Profile name: $isProfileNameEmpty | Profile avatar: $isAvatarEmpty | Needs PIN: $needsPin")

    SignalStore.internalValues().setForceEnterRestoreV2Flow(true)

    if (!needsProfile && !needsPin) {
      sharedViewModel.completeRegistration()
    }
    sharedViewModel.setInProgress(false)

    val startIntent = MainActivity.clearTop(activity).apply {
      if (needsPin) {
        putExtra("next_intent", CreateSvrPinActivity.getIntentForPinCreate(activity))
      }

      if (needsProfile) {
        putExtra("next_intent", CreateProfileActivity.getIntentForUserProfile(activity))
      }
    }

    Log.d(TAG, "Launching ${startIntent.component}")
    activity.startActivity(startIntent)
    activity.finish()
    ActivityNavigator.applyPopAnimationsToPendingTransition(activity)
  }

  private fun popBackStack() {
    sharedViewModel.setRegistrationCheckpoint(RegistrationCheckpoint.PUSH_NETWORK_AUDITED)
    NavHostFragment.findNavController(this).popBackStack()
  }

  private class PhoneStateCallback : SignalStrengthPhoneStateListener.Callback {
    override fun onNoCellSignalPresent() {
      // TODO [regv2]: animate in bottom sheet
    }

    override fun onCellSignalPresent() {
      // TODO [regv2]: animate in bottom sheet
    }
  }
}
