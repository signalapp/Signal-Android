/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.verify

import android.animation.Animator
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import com.airbnb.lottie.LottieDrawable
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.animation.AnimationCompleteListener
import org.thoughtcrime.securesms.components.FixedRoundedCornerBottomSheetDialogFragment
import org.thoughtcrime.securesms.components.ViewBinderDelegate
import org.thoughtcrime.securesms.databinding.SafetyNumberPnpEducationBottomSheetBinding
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.BottomSheetUtil
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.visible

class PnpSafetyNumberEducationDialogFragment : FixedRoundedCornerBottomSheetDialogFragment() {
  private val binding by ViewBinderDelegate(SafetyNumberPnpEducationBottomSheetBinding::bind)

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    return inflater.inflate(R.layout.safety_number_pnp_education_bottom_sheet, container, false)
  }

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
    dialog.behavior.skipCollapsed = true
    dialog.setOnShowListener {
      dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }
    return dialog
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    binding.lottie.visible = true
    binding.lottie.playAnimation()
    binding.lottie.addAnimatorListener(object : AnimationCompleteListener() {
      override fun onAnimationEnd(animation: Animator) {
        binding.lottie.removeAnimatorListener(this)
        binding.lottie.setMinAndMaxFrame(60, 360)
        binding.lottie.repeatMode = LottieDrawable.RESTART
        binding.lottie.repeatCount = LottieDrawable.INFINITE
        binding.lottie.frame = 60
        binding.lottie.playAnimation()
      }
    })

    binding.okay.setOnClickListener {
      SignalStore.uiHints().markHasSeenSafetyNumberUpdateNux()
      dismiss()
    }

    binding.help.setOnClickListener {
      CommunicationActions.openBrowserLink(requireContext(), "https://support.signal.org/hc/en-us/articles/360007060632")
    }
  }

  companion object {
    @JvmStatic
    fun show(fragmentManager: FragmentManager) {
      val fragment = PnpSafetyNumberEducationDialogFragment()
      if (fragmentManager.findFragmentByTag(BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG) == null) {
        fragment.show(fragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
      }
    }
  }
}
