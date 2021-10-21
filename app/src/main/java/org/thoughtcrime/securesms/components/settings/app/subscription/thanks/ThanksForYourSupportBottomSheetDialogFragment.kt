package org.thoughtcrime.securesms.components.settings.app.subscription.thanks

import android.animation.Animator
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.navigation.fragment.findNavController
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieDrawable
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.animation.AnimationCompleteListener
import org.thoughtcrime.securesms.badges.BadgeImageView
import org.thoughtcrime.securesms.badges.BadgeRepository
import org.thoughtcrime.securesms.components.FixedRoundedCornerBottomSheetDialogFragment
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.visible

class ThanksForYourSupportBottomSheetDialogFragment : FixedRoundedCornerBottomSheetDialogFragment() {

  override val peekHeightPercentage: Float = 1f

  private lateinit var switch: SwitchMaterial
  private lateinit var heading: TextView

  private lateinit var badgeRepository: BadgeRepository
  private lateinit var controlState: ControlState

  private enum class ControlState {
    FEATURE,
    DISPLAY
  }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
    return inflater.inflate(R.layout.thanks_for_your_support_bottom_sheet_dialog_fragment, container, false)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    badgeRepository = BadgeRepository(requireContext())

    val badgeView: BadgeImageView = view.findViewById(R.id.thanks_bottom_sheet_badge)
    val lottie: LottieAnimationView = view.findViewById(R.id.thanks_bottom_sheet_lottie)
    val badgeName: TextView = view.findViewById(R.id.thanks_bottom_sheet_badge_name)
    val done: MaterialButton = view.findViewById(R.id.thanks_bottom_sheet_done)
    val controlText: TextView = view.findViewById(R.id.thanks_bottom_sheet_control_text)
    val controlNote: View = view.findViewById(R.id.thanks_bottom_sheet_featured_note)

    heading = view.findViewById(R.id.thanks_bottom_sheet_heading)
    switch = view.findViewById(R.id.thanks_bottom_sheet_switch)

    val args = ThanksForYourSupportBottomSheetDialogFragmentArgs.fromBundle(requireArguments())

    badgeView.setBadge(args.badge)
    badgeName.text = args.badge.name

    val otherBadges = Recipient.self().badges.filterNot { it.id == args.badge.id }
    val hasOtherBadges = otherBadges.isNotEmpty()
    val displayingBadges = otherBadges.all { it.visible }

    if (hasOtherBadges && displayingBadges) {
      switch.isChecked = false
      controlText.setText(R.string.SubscribeThanksForYourSupportBottomSheetDialogFragment__make_featured_badge)
      controlNote.visible = true
      controlState = ControlState.FEATURE
    } else if (hasOtherBadges && !displayingBadges) {
      switch.isChecked = false
      controlText.setText(R.string.SubscribeThanksForYourSupportBottomSheetDialogFragment__display_on_profile)
      controlNote.visible = false
      controlState = ControlState.DISPLAY
    } else {
      switch.isChecked = true
      controlText.setText(R.string.SubscribeThanksForYourSupportBottomSheetDialogFragment__display_on_profile)
      controlNote.visible = false
      controlState = ControlState.DISPLAY
    }

    if (args.isBoost) {
      presentBoostCopy()
      lottie.visible = true
      lottie.playAnimation()
      lottie.addAnimatorListener(object : AnimationCompleteListener() {
        override fun onAnimationEnd(animation: Animator?) {
          lottie.removeAnimatorListener(this)
          lottie.setMinAndMaxFrame(30, 91)
          lottie.repeatMode = LottieDrawable.RESTART
          lottie.repeatCount = LottieDrawable.INFINITE
          lottie.frame = 30
          lottie.playAnimation()
        }
      })
    } else {
      presentSubscriptionCopy()
      lottie.visible = false
    }

    done.setOnClickListener { dismissAllowingStateLoss() }
  }

  override fun onDismiss(dialog: DialogInterface) {
    val controlChecked = switch.isChecked
    val args = ThanksForYourSupportBottomSheetDialogFragmentArgs.fromBundle(requireArguments())

    if (controlState == ControlState.DISPLAY) {
      badgeRepository.setVisibilityForAllBadges(controlChecked).subscribe()
    } else {
      badgeRepository.setFeaturedBadge(args.badge).subscribe()
    }

    if (args.isBoost) {
      findNavController().popBackStack()
    } else {
      requireActivity().finish()
      requireActivity().startActivity(AppSettingsActivity.subscriptions(requireContext()))
    }
  }

  private fun presentBoostCopy() {
    heading.setText(R.string.SubscribeThanksForYourSupportBottomSheetDialogFragment__thanks_for_the_boost)
  }

  private fun presentSubscriptionCopy() {
    heading.setText(R.string.SubscribeThanksForYourSupportBottomSheetDialogFragment__thanks_for_your_support)
  }
}
